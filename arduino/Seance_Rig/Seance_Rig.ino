/*
  Seance: rig firmware (Arduino Uno)

  The rig reads the frequency dial and the sound detector, sends a status line to the
  PC ten times a second, and carries out the PC's commands for the servo, RGB LED and
  buzzer. It holds no game state: every decision is made by the Java program.

  Link to the PC: USB serial at 9600 baud by default.
  Set USE_XBEE to 1 to talk through the xBee instead (xBee tx -> pin 10, xBee rx -> pin 11).

  Rig -> PC, every 100 ms:
    R,<dial 0-1023>,<sound peak 0-1023>,<servo angle 0-180>,<servo moving 0/1>

  PC -> Rig, one command per line:
    SERVO,<angle>      move slowly to an angle (0 to 180)
    SPEED,<ms>         servo speed in ms per degree (default 12, higher is slower)
    LED,<r>,<g>,<b>    set the RGB colour, each value 0 to 255
    CLICKS,<gapMs>     repeat Sweep clicks with this gap between them (0 stops them)
    ALARM,<count>      sound the hunt alarm this many times
    BEEP,<ms>          one beep lasting this many milliseconds
    STOP               silence the buzzer
    PING               the rig answers PONG

  Wiring: sound ENVELOPE -> A0, dial middle pin -> A1, buzzer I/O -> 8,
          RGB R -> 6, G -> 3, B -> 5 (common anode, + to 5V), servo signal -> 9.
*/
#include <Servo.h>
#include <SoftwareSerial.h>

#define USE_XBEE 0

// ---------- Pins ----------
const int SOUND_PIN  = A0;
const int DIAL_PIN   = A1;
const int BUZZER_PIN = 8;
const int R_PIN = 6, G_PIN = 3, B_PIN = 5;   // matched to how the LED is actually wired
const int SERVO_PIN  = 9;

// The buzzer module sounds when its pin is LOW
const int BUZZ_ON  = LOW;
const int BUZZ_OFF = HIGH;

const unsigned long REPORT_MS = 100;     // how often the status line is sent
const unsigned long SAMPLE_US = 2000;    // how often the sensors are sampled

// ---------- Link to the PC ----------
SoftwareSerial xbee(10, 11);             // RX, TX
Stream* link;

// ---------- Servo state ----------
Servo arm;
int servoAngle  = 90;                    // where the arm is now
int servoTarget = 90;                    // where the PC wants it
int msPerDegree = 12;
bool servoAttached = false;
unsigned long lastServoStep = 0;
unsigned long servoArrivedAt = 0;

// ---------- Buzzer state ----------
enum BuzzMode { BUZZ_IDLE, BUZZ_CLICKS, BUZZ_ALARM, BUZZ_BEEP };
BuzzMode buzzMode = BUZZ_IDLE;
bool buzzerOn = false;
unsigned long buzzTimer = 0;
int clickGap  = 0;
int alarmLeft = 0;
int beepMs    = 0;

// ---------- Sensor readings between reports ----------
int  soundPeak = 0;
long dialSum   = 0;
int  dialCount = 0;
unsigned long lastSample = 0;
unsigned long lastReport = 0;

// ---------- Incoming command buffer ----------
char line[40];
int  lineLen = 0;

// ===================================================================
// RGB LED (common anode, so a colour is on when its pin is LOW)
// ===================================================================
void setColour(int r, int g, int b) {
  analogWrite(R_PIN, 255 - constrain(r, 0, 255));
  analogWrite(G_PIN, 255 - constrain(g, 0, 255));
  analogWrite(B_PIN, 255 - constrain(b, 0, 255));
}

// ===================================================================
// Buzzer: a small state machine so beeps never block the loop
// ===================================================================
void buzzer(bool on) {
  digitalWrite(BUZZER_PIN, on ? BUZZ_ON : BUZZ_OFF);
  buzzerOn = on;
}

void startClicks(int gap) {
  if (gap <= 0) { buzzer(false); buzzMode = BUZZ_IDLE; return; }
  clickGap = gap;
  buzzMode = BUZZ_CLICKS;
  buzzer(false);
  buzzTimer = millis() - gap;           // first click comes straight away
}

void startAlarm(int count) {
  if (count <= 0) return;
  alarmLeft = count;
  buzzMode = BUZZ_ALARM;
  buzzer(true);
  buzzTimer = millis();
}

void startBeep(int ms) {
  if (ms <= 0) return;
  beepMs = ms;
  buzzMode = BUZZ_BEEP;
  buzzer(true);
  buzzTimer = millis();
}

void updateBuzzer(unsigned long now) {
  switch (buzzMode) {
    case BUZZ_CLICKS:                    // 15 ms click, then a gap
      if (buzzerOn && now - buzzTimer >= 15) { buzzer(false); buzzTimer = now; }
      else if (!buzzerOn && now - buzzTimer >= (unsigned long)clickGap) { buzzer(true); buzzTimer = now; }
      break;
    case BUZZ_ALARM:                     // 250 ms on, 150 ms off, count times
      if (buzzerOn && now - buzzTimer >= 250) {
        buzzer(false); buzzTimer = now;
        if (--alarmLeft <= 0) buzzMode = BUZZ_IDLE;
      } else if (!buzzerOn && now - buzzTimer >= 150) {
        buzzer(true); buzzTimer = now;
      }
      break;
    case BUZZ_BEEP:
      if (now - buzzTimer >= (unsigned long)beepMs) { buzzer(false); buzzMode = BUZZ_IDLE; }
      break;
    default:
      break;
  }
}

// ===================================================================
// Servo: moves one degree at a time, then detaches so it does not jitter
// ===================================================================
void updateServo(unsigned long now) {
  if (servoAngle != servoTarget) {
    if (!servoAttached) {
      arm.attach(SERVO_PIN);
      arm.write(servoAngle);
      servoAttached = true;
    }
    if (now - lastServoStep >= (unsigned long)msPerDegree) {
      servoAngle += (servoTarget > servoAngle) ? 1 : -1;
      arm.write(servoAngle);
      lastServoStep = now;
      if (servoAngle == servoTarget) servoArrivedAt = now;
    }
  } else if (servoAttached && now - servoArrivedAt >= 300) {
    arm.detach();                        // arrived and settled: stop the signal
    servoAttached = false;
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

  if      (strcmp(name, "SERVO") == 0 && a)            servoTarget = constrain(atoi(a), 0, 180);
  else if (strcmp(name, "SPEED") == 0 && a)            msPerDegree = constrain(atoi(a), 1, 200);
  else if (strcmp(name, "LED") == 0 && a && b && c)    setColour(atoi(a), atoi(b), atoi(c));
  else if (strcmp(name, "CLICKS") == 0 && a)           startClicks(atoi(a));
  else if (strcmp(name, "ALARM") == 0 && a)            startAlarm(atoi(a));
  else if (strcmp(name, "BEEP") == 0 && a)             startBeep(atoi(a));
  else if (strcmp(name, "STOP") == 0)                  { buzzer(false); buzzMode = BUZZ_IDLE; }
  else if (strcmp(name, "PING") == 0)                  link->println("PONG");
}

void readLink() {
  while (link->available()) {
    char ch = link->read();
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
// Sensors and the status line
// ===================================================================
void sampleSensors() {
  int s = analogRead(SOUND_PIN);
  if (s > soundPeak) soundPeak = s;       // keep the loudest moment since the last report
  dialSum += analogRead(DIAL_PIN);        // average the dial to smooth out noise
  dialCount++;
}

void sendReport() {
  int dial = (dialCount > 0) ? dialSum / dialCount : analogRead(DIAL_PIN);
  link->print("R,");
  link->print(dial);            link->print(',');
  link->print(soundPeak);       link->print(',');
  link->print(servoAngle);      link->print(',');
  link->println(servoAngle != servoTarget ? 1 : 0);
  soundPeak = 0;
  dialSum = 0;
  dialCount = 0;
}

// ===================================================================
void setup() {
  Serial.begin(9600);
#if USE_XBEE
  xbee.begin(9600);
  link = &xbee;
#else
  link = &Serial;
#endif

  pinMode(BUZZER_PIN, OUTPUT);
  buzzer(false);
  pinMode(R_PIN, OUTPUT);
  pinMode(G_PIN, OUTPUT);
  pinMode(B_PIN, OUTPUT);
  setColour(0, 0, 0);

  arm.attach(SERVO_PIN);                  // centre the arm once, then let updateServo release it
  arm.write(servoAngle);
  servoAttached = true;
  servoArrivedAt = millis();

  link->println("R,READY");
}

void loop() {
  unsigned long now = millis();

  readLink();
  updateServo(now);
  updateBuzzer(now);

  if (micros() - lastSample >= SAMPLE_US) {
    lastSample = micros();
    sampleSensors();
  }
  if (now - lastReport >= REPORT_MS) {
    lastReport = now;
    sendReport();
  }
}
