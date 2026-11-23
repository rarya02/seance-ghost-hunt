/*
  Rig LED + Servo Test (Arduino Uno)
  A standalone test, separate from the game. Upload it, open the Serial Monitor
  at 9600 baud with line ending "Newline", and follow what it prints.

  Wiring (same as the game):
    RGB LED, common anode: long leg -> 5V, R -> pin 6, G -> pin 3, B -> pin 5
    Servo: signal -> pin 9, red -> 5V, brown or black -> GND

  When it starts it runs an automatic test once: the LED colours, then the servo angles.
  After that, type a command and press Enter:
    r  g  b      red, green, blue
    o            orange (the game's middle sanity colour)
    w            white
    x            LED off
    p            fade each colour up and down on its own (checks each pin)
    0 to 180     move the servo slowly to that angle
    s            sweep the servo 0 -> 180 -> 0
    c            quick check: positional or continuous servo?
    a            run the automatic test again
    h            show this list

  If "BOOT" appears again while the servo is moving, the Uno restarted:
  the servo is pulling too much power from the USB.
*/
#include <Servo.h>

const int R_PIN = 6, G_PIN = 3, B_PIN = 5;   // matched to how the LED is actually wired
const int SERVO_PIN = 9;
const int MS_PER_DEGREE = 12;     // how slowly the servo moves (higher = slower)

Servo arm;
int angle = 90;                   // where the servo arm is now
char buf[16];                     // the command being typed
byte len = 0;

// ---------- LED ----------
// Common anode: a colour is ON when its pin is LOW, so the value is flipped.
void setColour(int r, int g, int b) {
  analogWrite(R_PIN, 255 - r);
  analogWrite(G_PIN, 255 - g);
  analogWrite(B_PIN, 255 - b);
}

void showColour(const __FlashStringHelper *name, int r, int g, int b) {
  setColour(r, g, b);
  Serial.print(F("LED -> "));
  Serial.println(name);
}

// Fades one pin up and down while the other two stay off.
void fadeOne(const __FlashStringHelper *name, int pin) {
  Serial.print(F("Fading "));
  Serial.print(name);
  Serial.println(F(" only: it should glow up and down smoothly, no other colour"));
  setColour(0, 0, 0);
  for (int v = 0; v <= 255; v += 5) { analogWrite(pin, 255 - v); delay(15); }
  for (int v = 255; v >= 0; v -= 5) { analogWrite(pin, 255 - v); delay(15); }
  setColour(0, 0, 0);
}

// ---------- Servo ----------
// Moves one degree at a time, then stops the signal so the servo does not jitter.
void moveTo(int target) {
  target = constrain(target, 0, 180);
  Serial.print(F("Servo "));
  Serial.print(angle);
  Serial.print(F(" -> "));
  Serial.println(target);
  arm.attach(SERVO_PIN);
  arm.write(angle);
  while (angle != target) {
    angle += (target > angle) ? 1 : -1;
    arm.write(angle);
    delay(MS_PER_DEGREE);
  }
  delay(400);                     // let it settle
  arm.detach();
  Serial.println(F("  arrived: the arm should now be still"));
}

// A positional servo goes middle, end, other end and stops each time.
// A continuous servo instead stops, spins one way, then spins the other way.
void quickCheck() {
  Serial.println(F("Quick check, watch the arm:"));
  arm.attach(SERVO_PIN);
  Serial.println(F("  90 for 2 s"));  arm.write(90);  delay(2000);
  Serial.println(F("  0 for 2 s"));   arm.write(0);   delay(2000);
  Serial.println(F("  180 for 2 s")); arm.write(180); delay(2000);
  Serial.println(F("  90 for 2 s"));  arm.write(90);  delay(2000);
  arm.detach();
  angle = 90;
  Serial.println(F("Positional: it went middle, one end, other end, middle and stopped each time."));
  Serial.println(F("Continuous: it kept spinning on 0 and 180 (one way, then the other)."));
}

void autoTest() {
  Serial.println(F("\n--- LED: each colour for 2 seconds ---"));
  showColour(F("RED"), 255, 0, 0);       delay(2000);
  showColour(F("GREEN"), 0, 255, 0);     delay(2000);
  showColour(F("BLUE"), 0, 0, 255);      delay(2000);
  showColour(F("ORANGE"), 255, 120, 0);  delay(2000);
  showColour(F("WHITE"), 255, 255, 255); delay(2000);
  showColour(F("OFF"), 0, 0, 0);

  Serial.println(F("\n--- Servo: middle, one end, other end, middle ---"));
  moveTo(90);  delay(800);
  moveTo(0);   delay(800);
  moveTo(180); delay(800);
  moveTo(90);
  Serial.println(F("\nAutomatic test done. Type h and Enter for the commands."));
}

void help() {
  Serial.println(F("\nCommands (type, then Enter):"));
  Serial.println(F("  r g b      red, green, blue"));
  Serial.println(F("  o          orange      w  white      x  off"));
  Serial.println(F("  p          fade each colour on its own"));
  Serial.println(F("  0 to 180   move the servo to that angle"));
  Serial.println(F("  s          sweep 0 -> 180 -> 0"));
  Serial.println(F("  c          quick check: positional or continuous servo?"));
  Serial.println(F("  a          run the automatic test again"));
}

void handle(char *cmd) {
  if (isDigit(cmd[0])) { moveTo(atoi(cmd)); return; }
  switch (tolower(cmd[0])) {
    case 'r': showColour(F("RED"), 255, 0, 0); break;
    case 'g': showColour(F("GREEN"), 0, 255, 0); break;
    case 'b': showColour(F("BLUE"), 0, 0, 255); break;
    case 'o': showColour(F("ORANGE"), 255, 120, 0); break;
    case 'w': showColour(F("WHITE"), 255, 255, 255); break;
    case 'x': showColour(F("OFF"), 0, 0, 0); break;
    case 'p': fadeOne(F("RED"), R_PIN); fadeOne(F("GREEN"), G_PIN); fadeOne(F("BLUE"), B_PIN); break;
    case 's': moveTo(0); moveTo(180); moveTo(0); break;
    case 'c': quickCheck(); break;
    case 'a': autoTest(); break;
    case 'h': help(); break;
    default:  Serial.println(F("Unknown command, type h for the list"));
  }
}

void setup() {
  Serial.begin(9600);
  pinMode(R_PIN, OUTPUT);
  pinMode(G_PIN, OUTPUT);
  pinMode(B_PIN, OUTPUT);
  setColour(0, 0, 0);
  delay(500);
  Serial.println(F("\n========== BOOT =========="));
  Serial.println(F("Rig LED + Servo Test. If BOOT shows again by itself, the Uno restarted."));
  arm.attach(SERVO_PIN);
  arm.write(angle);
  delay(600);
  arm.detach();
  autoTest();
}

void loop() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (len > 0) {
        buf[len] = 0;
        handle(buf);
        len = 0;
      }
    } else if (len < sizeof(buf) - 1) {
      buf[len++] = c;
    }
  }
}
