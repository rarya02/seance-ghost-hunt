/*
  Wand xBee Check (LilyPad)
  Talks to the wand's OWN xBee over the two data wires, with no radio involved, using the
  xBee's command mode (+++). If the wires are right, the xBee answers OK and this sketch
  prints its settings and whether it has joined the network.

  It tries the game's wiring first (LilyPad RX = pin 10, TX = pin 11), then the other way round.

  How to use: keep the LilyPad plugged into the Mac by USB/FTDI, upload, then open the
  Serial Monitor on the LILYPAD's port (not the Explorer's) at 9600 baud.
  Press the LilyPad's reset button to run it again.
*/
#include <SoftwareSerial.h>

SoftwareSerial orderA(10, 11);   // RX 10, TX 11: the game sketch's setting
SoftwareSerial orderB(11, 10);   // RX 11, TX 10: the two wires swapped

// Reads one reply line from the xBee (it ends replies with a carriage return).
void readReply(SoftwareSerial &p, char *out, int max, unsigned long ms) {
  unsigned long start = millis();
  int n = 0;
  while (millis() - start < ms) {
    if (p.available()) {
      char c = p.read();
      if (c == '\r') {
        if (n > 0) break;
        continue;
      }
      if (n < max - 1 && c >= ' ') out[n++] = c;
    }
  }
  out[n] = 0;
}

// Command mode needs about a second of silence, then +++ with nothing after it.
bool enterCommandMode(SoftwareSerial &p) {
  p.begin(9600);
  p.listen();
  delay(1200);
  while (p.available()) p.read();
  p.print("+++");
  char reply[16];
  readReply(p, reply, sizeof(reply), 2500);
  return strcmp(reply, "OK") == 0;
}

void ask(SoftwareSerial &p, const char *cmd, const char *meaning) {
  p.print(cmd);
  p.print('\r');
  char reply[24];
  readReply(p, reply, sizeof(reply), 1000);
  Serial.print("  ");
  Serial.print(cmd);
  Serial.print(" = ");
  Serial.print(reply[0] ? reply : "(no answer)");
  Serial.print("    ");
  Serial.println(meaning);
}

void report(SoftwareSerial &p, const char *wiring) {
  Serial.print("The xBee ANSWERED. Wiring that works: ");
  Serial.println(wiring);
  Serial.println("Its settings:");
  ask(p, "ATVR", "firmware: 21xx coordinator, 23xx router, 29xx end device (ZigBee); 10xx = Series 1");
  ask(p, "ATID", "PAN ID: must be the same as the Explorer's xBee");
  ask(p, "ATCH", "radio channel in use");
  ask(p, "ATMY", "this xBee's network address");
  ask(p, "ATDH", "send-to address, high part: 0 to reach the coordinator");
  ask(p, "ATDL", "send-to address, low part: 0 to reach the coordinator");
  ask(p, "ATSH", "serial number, high part");
  ask(p, "ATSL", "serial number, low part");
  ask(p, "ATBD", "baud: 3 = 9600");
  ask(p, "ATAI", "0 = joined the network, anything else = NOT joined");
  p.print("ATCN\r");                   // leave command mode
  Serial.println("Done.");
}

void setup() {
  Serial.begin(9600);
  delay(1500);
  Serial.println();
  Serial.println("=== Wand xBee check: LilyPad <-> its own xBee, over the wires ===");

  Serial.println("Trying RX = pin 10, TX = pin 11 (the game sketch's wiring)...");
  if (enterCommandMode(orderA)) {
    report(orderA, "RX 10, TX 11, same as the game sketch");
    return;
  }
  orderA.end();
  Serial.println("  no answer");

  Serial.println("Trying RX = pin 11, TX = pin 10 (wires the other way)...");
  if (enterCommandMode(orderB)) {
    report(orderB, "RX 11, TX 10: SWAPPED, the game sketch needs xbee(11, 10)");
    return;
  }
  orderB.end();
  Serial.println("  no answer");
  Serial.println();
  Serial.println("No answer either way: the LilyPad cannot talk to its xBee.");
  Serial.println("Check: xBee board + and - wired to the LilyPad, xBee fully seated,");
  Serial.println("and both data wires go from the xBee board's rx/tx to pins 10 and 11.");
}

void loop() {
}
