/*
  Wand Radio Test (LilyPad + xBee)
  A standalone test, separate from the game. It checks the radio link between the wand's
  xBee and the xBee Explorer on the Mac, in both directions, and finds which pin is which.

  Every second the wand sends two lines, one through each pin:
    HELLO 12 sent on pin 11     <- what the game sketch uses (RX 10, TX 11)
    HELLO 12 sent on pin 10     <- only arrives if the two xBee wires are the other way round
  It also listens for anything typed in the Serial Monitor (alternating which pin it listens
  on). When it hears something it buzzes and answers "WAND GOT ... ON RX PIN ...".

  The LilyPad's own LED (pin 13) blinks each time it sends, and one buzz at start-up shows
  the sketch is running.

  How to use: upload, power the wand, then open the Serial Monitor on the xBee Explorer's
  port (/dev/cu.usbserial-...) at 9600 baud, line ending Newline. Type x and Enter to test
  the Mac -> wand direction.
*/
#include <SoftwareSerial.h>

SoftwareSerial viaPin11(10, 11);   // RX 10, TX 11: the game sketch's setting
SoftwareSerial viaPin10(11, 10);   // RX 11, TX 10: the two wires swapped
const int MOTOR_PIN = 3;
const int LED_PIN = 13;
unsigned long count = 0;

void buzz(int ms) {
  digitalWrite(MOTOR_PIN, HIGH);
  delay(ms);
  digitalWrite(MOTOR_PIN, LOW);
}

void sendOn(SoftwareSerial &port, const char *pinLabel, const char *text, unsigned long n) {
  port.begin(9600);
  port.print(text);
  port.print(n);
  port.print(" sent on pin ");
  port.println(pinLabel);
  port.end();
}

// Listens on one pin for a while; buzzes and reports back if anything arrives.
void listenOn(SoftwareSerial &port, const char *rxLabel, unsigned long ms) {
  port.begin(9600);
  port.listen();
  unsigned long start = millis();
  char got = 0;
  while (millis() - start < ms) {
    if (port.available()) {
      char c = port.read();
      if (c > ' ') got = c;
    }
  }
  port.end();
  if (got) {
    buzz(200);
    for (int i = 0; i < 2; i++) {
      SoftwareSerial &out = (i == 0) ? viaPin11 : viaPin10;
      out.begin(9600);
      out.print("WAND GOT '");
      out.print(got);
      out.print("' ON RX PIN ");
      out.println(rxLabel);
      out.end();
    }
    Serial.print("Got '");
    Serial.print(got);
    Serial.print("' on RX pin ");
    Serial.println(rxLabel);
  }
}

void setup() {
  pinMode(MOTOR_PIN, OUTPUT);
  pinMode(LED_PIN, OUTPUT);
  Serial.begin(9600);          // also prints to USB/FTDI, if the LilyPad is plugged in
  delay(500);
  buzz(300);                   // one buzz = the test is running
  Serial.println("Wand Radio Test running");
}

void loop() {
  count++;
  digitalWrite(LED_PIN, HIGH);
  sendOn(viaPin11, "11", "HELLO ", count);
  sendOn(viaPin10, "10", "HELLO ", count);
  Serial.print("HELLO ");
  Serial.println(count);
  digitalWrite(LED_PIN, LOW);

  if (count % 2 == 0) listenOn(viaPin11, "10", 900);   // the game sketch's RX pin
  else                listenOn(viaPin10, "11", 900);
}
