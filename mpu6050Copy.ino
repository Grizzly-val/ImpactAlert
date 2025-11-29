#include <Wire.h>
#include <MPU6050.h>
#include "BluetoothSerial.h"

MPU6050 mpu;
BluetoothSerial SerialBT;

#define BUZZER_PIN 2

const float IMPACT_G_THRESHOLD = 16;
const float DELTA_G_THRESHOLD  = 8;
const int   FILTER_WINDOW = 5;

const float TILT_THRESHOLD = 90.0;              // degrees
const unsigned long ORIENTATION_WINDOW = 7000;  // 7 sec to confirm crash
const unsigned long ALARM_DURATION = 3000;      // 3 sec buzzer + BT

float totalG_history[FILTER_WINDOW];
int history_index = 0;

// state machine
bool impactDetected = false;
unsigned long impactStartTime = 0;

bool alarmActive = false;
unsigned long alarmStartTime = 0;

unsigned long tiltStartTime = 0;

// --------------------- STATS TRACKERS -----------------------
float max_totalG = 0;
float min_totalG = 999;
float sum_totalG = 0;
unsigned long count_totalG = 0;

float max_deltaG = 0;
float min_deltaG = 999;
float sum_deltaG = 0;
unsigned long count_deltaG = 0;

unsigned long lastStatsPrint = 0;
// ------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  Wire.begin(21, 22);
  mpu.initialize();
  mpu.setFullScaleAccelRange(MPU6050_ACCEL_FS_16);

  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  for (int i = 0; i < FILTER_WINDOW; i++) totalG_history[i] = 1.0;

  if (!SerialBT.begin("ImpactAlert_Device"))
    Serial.println("Bluetooth failed!");
  else
    Serial.println("Bluetooth started: ImpactAlert_Device");

  Serial.println("Crash Detection System Ready");
}

float getTotalGFiltered(float totalG) {
  totalG_history[history_index] = totalG;
  history_index = (history_index + 1) % FILTER_WINDOW;

  float sum = 0;
  for (int i = 0; i < FILTER_WINDOW; i++) sum += totalG_history[i];
  return sum / FILTER_WINDOW;
}

float getTiltAngle(float x, float y, float z) {
  return atan2(sqrt(x*x + y*y), z) * 180.0 / PI;
}

void loop() {

  // ---------------- ALARM MODE ----------------
  if (alarmActive) {
    digitalWrite(BUZZER_PIN, HIGH);

    if (millis() - alarmStartTime >= ALARM_DURATION) {
      digitalWrite(BUZZER_PIN, LOW);
      alarmActive = false;
      Serial.println("Alarm finished. Resuming normal sensing.");
    }

    delay(50);
    return;
  }

  // ---------------- CRASH CONFIRMATION MODE ----------------
  if (impactDetected) {

    int16_t ax, ay, az, gx, gy, gz;
    mpu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);

    float Gx = ax / 2048.0;
    float Gy = ay / 2048.0;
    float Gz = az / 2048.0;

    float tilt = getTiltAngle(Gx, Gy, Gz);
    Serial.print("Checking orientation... Tilt = ");
    Serial.println(tilt);

    if (tilt >= TILT_THRESHOLD) {
      if (tiltStartTime == 0) tiltStartTime = millis();

      if (millis() - tiltStartTime >= 2000) { // 2 sec continuous tilt
        Serial.println("⚠️ CRASH CONFIRMED (tilt detected)!");
        SerialBT.println("CRASH");
        alarmActive = true;
        alarmStartTime = millis();
        impactDetected = false;
        tiltStartTime = 0;
        return;
      }
    } else {
      tiltStartTime = 0;
    }

    if (millis() - impactStartTime >= ORIENTATION_WINDOW) {
      Serial.println("No tilt detected. False alarm. Resuming normal monitoring.");
      impactDetected = false;
      tiltStartTime = 0;
    }

    delay(50);
    return;
  }

  // ---------------- NORMAL MODE ----------------
  int16_t ax, ay, az, gx, gy, gz;
  mpu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);

  float Gx = ax / 2048.0;
  float Gy = ay / 2048.0;
  float Gz = az / 2048.0;

  float totalG = sqrt(Gx*Gx + Gy*Gy + Gz*Gz);
  float totalG_filtered = getTotalGFiltered(totalG);

  static float prev_totalG = 1.0;
  float deltaG = abs(totalG_filtered - prev_totalG);
  prev_totalG = totalG_filtered;

  Serial.print("G: "); Serial.print(totalG_filtered);
  Serial.print(" | dG: "); Serial.print(deltaG);
  Serial.print(" | Tilt: "); Serial.println(getTiltAngle(Gx, Gy, Gz));

  // ----------- UPDATE STATS -------------
  max_totalG = max(max_totalG, totalG_filtered);
  min_totalG = min(min_totalG, totalG_filtered);
  sum_totalG += totalG_filtered;
  count_totalG++;

  max_deltaG = max(max_deltaG, deltaG);
  min_deltaG = min(min_deltaG, deltaG);
  sum_deltaG += deltaG;
  count_deltaG++;
  // --------------------------------------

  // ----------- PRINT STATS EVERY 10s -----------
  if (millis() - lastStatsPrint >= 10000) {
    lastStatsPrint = millis();

    float avgG = sum_totalG / count_totalG;
    float avgDelta = sum_deltaG / count_deltaG;

    Serial.println("-------- STATS --------");
    Serial.print("G | min: "); Serial.print(min_totalG);
    Serial.print(" | max: "); Serial.print(max_totalG);
    Serial.print(" | avg: "); Serial.println(avgG);

    Serial.print("dG | min: "); Serial.print(min_deltaG);
    Serial.print(" | max: "); Serial.print(max_deltaG);
    Serial.print(" | avg: "); Serial.println(avgDelta);
    Serial.println("--------------------------------");
    
    max_totalG = 0;
    min_totalG = 999;
    sum_totalG = 0;
    count_totalG = 0;

    max_deltaG = 0;
    min_deltaG = 999;
    sum_deltaG = 0;
    count_deltaG = 0;
  }
  // ----------------------------------------------

  // Impact detection
  if (totalG_filtered >= IMPACT_G_THRESHOLD && deltaG >= DELTA_G_THRESHOLD) {
    Serial.println("⚠️ IMPACT DETECTED! Waiting for tilt confirmation...");
    impactDetected = true;
    impactStartTime = millis();
    tiltStartTime = 0;
  }

  delay(50);
}
