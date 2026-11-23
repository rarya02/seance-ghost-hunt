/*
  Seance: wand firmware (LilyPad Arduino)

  The wand reads the Hall sensor, touch grip, light sensor and accelerometer, sends a
  status line to the PC through the xBee ten times a second, and runs the vibration
  motor when the PC asks. It holds no game state: every decision is made by the Java program.

  xBee (on the LilyPad xBee board): xBee tx -> pin 10, xBee rx -> pin 11, 9600 baud (as in Lab 8).

  Wand -> PC, every 100 ms:
    W,<hall 0/1>,<touch 0/1>,<taps>,<light 0-1023>,<pose>,<still 0/1>
      hall  1 when the magnet is under the tip
      touch 1 while the grip is held
      taps  running count of taps (0 to 999, then back to 0), so a quick tap is never missed
      pose  LEVEL, UP, DOWN, LEFT or RIGHT
      still 1 when the wand has been held steady for the last half second

  PC -> Wand, one command per line:
    VIB,<onMs>,<offMs>,<count>   vibration pulses (count 0 repeats until VIB,0)
    VIB,0                        stop vibrating (also stops a heartbeat)
    BUZZ,<ms>                    one vibration lasting this many milliseconds
    BEAT,<periodMs>              a lub-dub heartbeat every periodMs (BEAT,0 stops it)
  Repeating commands are re-sent by the PC every second in case the radio drops one;
  an identical repeat is ignored so the rhythm is not restarted.
    CAL                          take the current position as level
    IDLE                         nothing should be vibrating: stops a heartbeat or repeating
                                 pattern (a one-off buzz still finishes). The PC re-sends it
                                 every second while the wand should be quiet, so a lost
                                 "stop" is corrected within a second.
    PING                         the wand answers PONG

  Wiring: Hall S -> A0, accelerometer X -> A3, Y -> A2, Z -> A1, light S -> A4,
          touch SIG -> 2, Vibe Board + -> 3.
*/
#include <SoftwareSerial.h>

#define DEBUG 0          // 1 also prints every status line to the USB/FTDI serial monitor

// ---------- Pins ----------
const int HALL_PIN  = A0;
const int X_PIN = A3, Y_PIN = A2, Z_PIN = A1;
const int LIGHT_PIN = A4;
const int TOUCH_PIN = 2;
const int MOTOR_PIN = 3;

const unsigned long REPORT_MS  = 100;   // how often the status line is sent
const unsigned long SAMPLE_MS  = 5;     // how often the sensors are sampled
const unsigned long STILL_WINDOW_MS = 500;
const int TILT_THRESHOLD = 60;          // change from level that counts as a clear tilt
const int STILL_RANGE    = 20;          // max wobble on each axis that still counts as steady
const unsigned long TOUCH_DEBOUNCE_MS = 20;

SoftwareSerial xbee(10, 11);            // RX, TX

// ---------- Accelerometer ----------
int restX = 512, restY = 512;           // readings when the wand is level
int lastX = 512, lastY = 512, lastZ = 512;
long smoothX16 = 512L * 16, smoothY16 = 512L * 16;   // smoothed tilt, times 16 (so no decimals are needed)
const char* poseNow = "LEVEL";                       // the pose last reported, for hysteresis
int minX, maxX, minY, maxY, minZ, maxZ; // wobble in the current window
unsigned long windowStart = 0;
bool still = false;

// ---------- Touch ----------
bool touchState = false;
bool touchRaw = false;
unsigned long touchChangedAt = 0;
int taps = 0;

// ---------- Light ----------
long lightSum = 0;
int  lightCount = 0;

// ---------- Vibration ----------
bool vibActive = false;
bool vibOn = false;
int beatPeriod = 0;                      // heartbeat period in ms, 0 = no heartbeat
unsigned long beatStart = 0;
const int LUB_MS = 70, DUB_AT_MS = 180, DUB_MS = 70;   // the two thumps of one heartbeat
int  vibOnMs = 0, vibOffMs = 0, vibLeft = 0;   // vibLeft -1 means repeat until stopped
unsigned long vibTimer = 0;

unsigned long lastSample = 0, lastReport = 0;

char line[40];
int  lineLen = 0;

// ===================================================================
// Vibration motor: a small state machine so pulses never block the loop
// ===================================================================
void motor(bool on) {
  digitalWrite(MOTOR_PIN, on ? HIGH : LOW);
  vibOn = on;
}

void startVibration(int onMs, int offMs, int count) {
  if (onMs <= 0) { motor(false); vibActive = false; return; }
  vibOnMs = onMs;
  vibOffMs = max(offMs, 0);
  vibLeft = (count <= 0) ? -1 : count;
  vibActive = true;
  motor(true);
  vibTimer = millis();
}

// Heartbeat: lub ... dub ... rest, repeating. A one-off buzz takes priority while it runs.
void updateBeat(unsigned long now) {
  if (beatPeriod <= 0 || vibActive) return;
  unsigned long t = (now - beatStart) % (unsigned long)beatPeriod;
  bool on = t < (unsigned long)LUB_MS || (t >= (unsigned long)DUB_AT_MS && t < (unsigned long)(DUB_AT_MS + DUB_MS));
  if (on != vibOn) motor(on);
}

void updateVibration(unsigned long now) {
  if (!vibActive) return;
  if (vibOn && now - vibTimer >= (unsigned long)vibOnMs) {
    motor(false);
    vibTimer = now;
    if (vibLeft > 0 && --vibLeft == 0) vibActive = false;
  } else if (!vibOn && now - vibTimer >= (unsigned long)vibOffMs) {
    motor(true);
    vibTimer = now;
  }
}

// ===================================================================
// Accelerometer
// ===================================================================
void calibrate() {
  long sx = 0, sy = 0;
  for (int i = 0; i < 20; i++) {
    sx += analogRead(X_PIN);
    sy += analogRead(Y_PIN);
    delay(5);
  }
  restX = sx / 20;
  restY = sy / 20;
  smoothX16 = (long) restX * 16;
  smoothY16 = (long) restY * 16;
  poseNow = "LEVEL";
}

const char* currentPose() {
  int dx = (int) (smoothX16 / 16) - restX;
  int dy = (int) (smoothY16 / 16) - restY;
  // Hysteresis: a clear tilt is needed to leave LEVEL, but once tilted the pose holds
  // until the tilt drops well back, so it does not flicker at the edge.
  int threshold = (strcmp(poseNow, "LEVEL") == 0) ? TILT_THRESHOLD : TILT_THRESHOLD * 6 / 10;
  if (abs(dx) < threshold && abs(dy) < threshold) poseNow = "LEVEL";
  // Matched to how the accelerometer sits on our wand (tested):
  // Y tilts with the tip going up and down, X tilts with the tip going left and right.
  else if (abs(dy) > abs(dx)) poseNow = (dy < 0) ? "UP" : "DOWN";
  else poseNow = (dx > 0) ? "RIGHT" : "LEFT";
  return poseNow;
}

void resetStillWindow(unsigned long now) {
  minX = maxX = lastX;
  minY = maxY = lastY;
  minZ = maxZ = lastZ;
  windowStart = now;
}

void sampleAccel(unsigned long now) {
  lastX = analogRead(X_PIN);
  lastY = analogRead(Y_PIN);
  lastZ = analogRead(Z_PIN);
  // smoothing: each new sample moves the average 1/16 of the way (about 80 ms to settle),
  // so a shake, a tap on the grip or the motor does not flip the pose
  smoothX16 += lastX - smoothX16 / 16;
  smoothY16 += lastY - smoothY16 / 16;
  minX = min(minX, lastX); maxX = max(maxX, lastX);
  minY = min(minY, lastY); maxY = max(maxY, lastY);
  minZ = min(minZ, lastZ); maxZ = max(maxZ, lastZ);
  if (now - windowStart >= STILL_WINDOW_MS) {   // judge the window that just ended
    still = (maxX - minX <= STILL_RANGE) && (maxY - minY <= STILL_RANGE) && (maxZ - minZ <= STILL_RANGE);
    resetStillWindow(now);
  }
}

// ===================================================================
// Touch grip, with debounce and a running tap count
// ===================================================================
void sampleTouch(unsigned long now) {
  bool raw = digitalRead(TOUCH_PIN) == HIGH;
  if (raw != touchRaw) { touchRaw = raw; touchChangedAt = now; }
  if (touchRaw != touchState && now - touchChangedAt >= TOUCH_DEBOUNCE_MS) {
    touchState = touchRaw;
    if (touchState) taps = (taps + 1) % 1000;   // count each new touch as a tap
  }
}

// ===================================================================
// Commands from the PC
// ===================================================================
void handleCommand(char* cmd) {
  char* name = strtok(cmd, ",");
  if (name == NULL) return;
  char* a = strtok(NULL, ",");
  char* b = strtok(NULL, ",");
  char* c = strtok(NULL, ",");

  if (strcmp(name, "VIB") == 0 && a) {
    int onMs = atoi(a);
    int offMs = b ? atoi(b) : 0;
    int count = c ? atoi(c) : 1;
    if (onMs <= 0 || !b) { beatPeriod = 0; startVibration(0, 0, 0); }
    else if (count <= 0 && vibActive && vibLeft == -1 && onMs == vibOnMs && offMs == vibOffMs) {
      // the PC re-sent the same repeating pattern: keep the current rhythm going
    } else {
      if (count <= 0) beatPeriod = 0;               // a new repeating pattern replaces a heartbeat
      startVibration(onMs, offMs, count);
    }
  }
  else if (strcmp(name, "BUZZ") == 0 && a) startVibration(atoi(a), 0, 1);
  else if (strcmp(name, "BEAT") == 0 && a) {
    int period = atoi(a);
    if (period <= 0) {
      beatPeriod = 0;
      if (!vibActive) motor(false);
    } else if (period != beatPeriod) {             // an identical repeat keeps the rhythm going
      if (vibActive && vibLeft == -1) vibActive = false;   // replaces a repeating pattern
      beatPeriod = period;
      beatStart = millis();
    }
  }
  else if (strcmp(name, "CAL") == 0)       calibrate();
  else if (strcmp(name, "PING") == 0)      xbee.println("PONG");
  else if (strcmp(name, "IDLE") == 0) {
    beatPeriod = 0;
    if (vibActive && vibLeft == -1) vibActive = false;   // stop a repeating pattern
    if (!vibActive) motor(false);                        // a one-off buzz may finish
  }
}

void readLink() {
  while (xbee.available()) {
    char ch = xbee.read();
    if (ch == '\n' || ch == '\r') {
      if (lineLen > 0) {
        line[lineLen] = '\0';
        handleCommand(line);
        lineLen = 0;
      }
    } else if (lineLen < (int)sizeof(line) - 1) {
      line[lineLen++] = ch;
    }
  }
}

// ===================================================================
// Status line
// ===================================================================
void sendTo(Stream& out, int hall, int light, const char* pose) {
  out.print("W,");
  out.print(hall);               out.print(',');
  out.print(touchState ? 1 : 0); out.print(',');
  out.print(taps);               out.print(',');
  out.print(light);              out.print(',');
  out.print(pose);               out.print(',');
  out.println(still ? 1 : 0);
}

void sendReport() {
  int hall  = (digitalRead(HALL_PIN) == LOW) ? 1 : 0;   // the digital Hall switch pulls LOW near a magnet
  int light = (lightCount > 0) ? lightSum / lightCount : analogRead(LIGHT_PIN);
  const char* pose = currentPose();
  sendTo(xbee, hall, light, pose);
#if DEBUG
  sendTo(Serial, hall, light, pose);
#endif
  lightSum = 0;
  lightCount = 0;
}

// ===================================================================
void setup() {
  xbee.begin(9600);
#if DEBUG
  Serial.begin(9600);
#endif
  pinMode(HALL_PIN, INPUT_PULLUP);
  pinMode(TOUCH_PIN, INPUT);
  pinMode(MOTOR_PIN, OUTPUT);
  motor(false);

  delay(1000);                 // hold the wand level while it starts up
  calibrate();
  lastX = restX; lastY = restY; lastZ = analogRead(Z_PIN);
  resetStillWindow(millis());

  startVibration(80, 80, 3);   // three quick buzzes = wand is running
  xbee.println("W,READY");
}

void loop() {
  unsigned long now = millis();

  readLink();
  updateVibration(now);
  updateBeat(now);

  if (now - lastSample >= SAMPLE_MS) {
    lastSample = now;
    sampleAccel(now);
    sampleTouch(now);
    lightSum += analogRead(LIGHT_PIN);
    lightCount++;
  }
  if (now - lastReport >= REPORT_MS) {
    lastReport = now;
    sendReport();
  }
}
