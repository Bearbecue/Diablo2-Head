//----------------------------------------------------------------------------
//
//  Diablo2 wall-mounted head
//
//  TODO
//  - Add WIFI communication to make it a IOT?
//  - Allow sending the WIFI login info through the bluetooth device
//    - Either through the android app, or through a generic bluetooth terminal
//    - Record it in the EEPROM so you don't have to record it every time you power up the device !
//  - Correlate low-frequency intensity variations
//  - When dimming through software, reduce the contrast of the sinewaves to minimize flickering ?
//  - low-power mode & wake-on-lan / wake-on-bluetooth? use the interrupt pin D2 to wake ?
//
//----------------------------------------------------------------------------

#include <SoftwareSerial.h>
#include <EEPROM.h>

//----------------------------------------------------------------------------
// Version of the HW. 2 for Mk2, 4 for Mk4
#define HW_MK    4

//----------------------------------------------------------------------------

#if (HW_MK == 2)
# define PIN_LED_EYES_L       5
# define PIN_LED_EYES_R       6
# define PIN_LED_SOULSTONE_0  9
# define PIN_LED_SOULSTONE_1  10
# define PIN_LED_MOUTH_0      3
# define PIN_LED_MOUTH_1      11
# define PIN_WIFI_SERIAL_RX   2
# define PIN_WIFI_SERIAL_TX   8
#elif (HW_MK >= 4)
# define PIN_LED_EYES_L       5
# define PIN_LED_EYES_R       3
# define PIN_LED_SOULSTONE_0  9
# define PIN_LED_SOULSTONE_1  6
# define PIN_LED_MOUTH_0      10
# define PIN_LED_MOUTH_1      11  // Unused, single mouth output before Mk4
# define PIN_WIFI_SERIAL_RX   2
# define PIN_WIFI_SERIAL_TX   8
#endif

//----------------------------------------------------------------------------

const float   kTimeWrap = 1.0e+5f; // 11.5 days, 23 days before wrap as we start in the negative side
const float   kAnimationSpeed = 0.5f;
unsigned long prevFrameMs = 0;
float         cur_time = -kTimeWrap;
float         startupFader = 0.0f;

//----------------------------------------------------------------------------

SoftwareSerial SerialWifi(PIN_WIFI_SERIAL_RX, PIN_WIFI_SERIAL_TX);

//----------------------------------------------------------------------------
// These get changed by the bluetooth commands

float       ledFactor_Soulstone = 1.0f;
float       ledFactor_Eyes = 1.0f;
float       ledFactor_Mouth = 1.0f;
float       ledFactor_BaseVar = 1.0f;
float       ledFactor_BaseSync = 0.0f;

//----------------------------------------------------------------------------
// Helpers

int   clamp(int v, int vMin, int vMax) { return max(min(v, vMax), vMin); }
float clamp(float v, float vMin, float vMax) { return max(min(v, vMax), vMin); }
float lerp(float a, float b, float t) { return a + t * (b - a); }
float voltage_from_pin(float rawRead) { return rawRead * (5.0f / 1023.0f); }
int   pwm_from_brightness(float brightness, float overallScale, float base)
{
  const int pwmScale = 255;
//  const float b = pwmScale;
  const float b = pwmScale * (base + brightness * (1 - base));
  return min((int)(b * overallScale), pwmScale);
}

float mapBrightnessFactorToVariationScale(float bf)
{
  const float kThresholdA = 0.3f;
  const float kThresholdB = 0.05f;
  return clamp((bf - kThresholdB) / (kThresholdA - kThresholdB), 0.0f, 1.0f);
}

//----------------------------------------------------------------------------

float noise(float t)
{
  t *= 2 * 3.14159f;
  const float k0 = 1.0f;
  const float k1 = 0.75f;
  const float k2 = 0.6f;
  const float n0 = sin(t * 1.000f) * k0;
  const float n1 = sin(t * 1.812f) * k1;
  const float n2 = sin(t * 3.347f) * k2;
  const float n = (n0 + n1 + n2) / (k0 + k1 + k2);
  return n * 0.5f + 0.5f;
}

//----------------------------------------------------------------------------

void setup()
{
  // Set mode of all output PWM pins
  pinMode(PIN_LED_EYES_L, OUTPUT);
  pinMode(PIN_LED_EYES_R, OUTPUT);
  pinMode(PIN_LED_SOULSTONE_0, OUTPUT);
  pinMode(PIN_LED_SOULSTONE_1, OUTPUT);
  pinMode(PIN_LED_MOUTH_0, OUTPUT);
  pinMode(PIN_LED_MOUTH_1, OUTPUT);

  // Init serial com for HC-06 module
  Serial.begin(9600);
  // Init serial com for ESP-01 module
  SerialWifi.begin(57600);  // don't use 9600, arduino docs says lower baud rates can interfere badly with HW serial (?)

  // EEPROM: Detect if the data contents of the EEPROM are valid.
  // Don't bother computing an actual CRC. We simply want to see if the data is properly initialized.
  // So just compute a simple sum. The 512 bytes are factory-initialized to 0xFF
  // so the sum of the 508 first bytes (stored in the 4 last bytes), would be 0x0001FA04, instead of 0xFFFFFFFF
  if (eeprom_compute_sum() != eeprom_read_sum())
    eeprom_reset();

  startupFader = 0.0f;

  prevFrameMs = millis();
}

//----------------------------------------------------------------------------

void loop()
{
  ReadSerialInput();

  const float potScale = 1.0f;

  // TODO: Right now all noise waveforms are unrelated.
  // We might want to couple some low-frequency variations across all lights
  // IE: See this as the overall intensity of the "internal fire"
  // Then the higher-frequency noise changes per-light location.
  // Kind-of similar to sampling a 3D noise based on the position of the light
  // They're all roughly around the head in space, so pick-up the same low-frequencies,
  // but the higher we go in frequency, the more their individual location matters and change
  // the sampled values.
  const float brightnessBaseline = noise(cur_time * 1.0f - 5.0f);
  const float brightnessBaselineWeight = 1.0f - ledFactor_BaseSync;

  // Soulstone brightness
  // 2 driver pins, 6 LEDs (3 per pin)
  const float varScale0 = mapBrightnessFactorToVariationScale(ledFactor_Soulstone);
  const float brightness0 = lerp(brightnessBaseline, noise(cur_time * 0.8f +  0.0f), brightnessBaselineWeight);
  const float brightness1 = lerp(brightnessBaseline, noise(cur_time * 1.2f + 12.345f), brightnessBaselineWeight);
  const float baseSS = lerp(1.0f, lerp(1.0f, 0.08f, ledFactor_BaseVar), varScale0);
  const float scaleSS = ledFactor_Soulstone * startupFader;
  analogWrite(PIN_LED_SOULSTONE_0, pwm_from_brightness(brightness0, scaleSS, baseSS));
  analogWrite(PIN_LED_SOULSTONE_1, pwm_from_brightness(brightness1, scaleSS, baseSS));

  // Eyes brightness
  //  2 driver pins, 2 LEDs
  const float varScale1 = mapBrightnessFactorToVariationScale(ledFactor_Eyes);
  const float brightness2 = lerp(brightnessBaseline, noise(cur_time * 0.5f +   0.0f), brightnessBaselineWeight);
  const float brightness3 = lerp(brightnessBaseline, noise(cur_time * 0.5f + 123.456f), brightnessBaselineWeight);
  const float baseEY = lerp(1.0f, lerp(1.0f, 0.5f, ledFactor_BaseVar), varScale1);
  const float scaleEY = ledFactor_Eyes * startupFader;
  analogWrite(PIN_LED_EYES_L, pwm_from_brightness(brightness2, scaleEY, baseEY));
  analogWrite(PIN_LED_EYES_R, pwm_from_brightness(brightness3, scaleEY, baseEY));

  // Mouth brightness
  //  1 driver pin, 3 LEDs
  const float varScale2 = mapBrightnessFactorToVariationScale(ledFactor_Mouth);
  const float brightness4 = lerp(brightnessBaseline, noise(cur_time * 2.0f + 0.0f), brightnessBaselineWeight);
  const float brightness5 = lerp(brightnessBaseline, noise(cur_time * 3.0f + 9.876f), brightnessBaselineWeight);
  const float baseMT = lerp(1.0f, lerp(1.0f, 0.33f, ledFactor_BaseVar), varScale2);
  const float scaleMT = ledFactor_Mouth * startupFader;
  analogWrite(PIN_LED_MOUTH_0, pwm_from_brightness(brightness4, scaleMT, baseMT));
  analogWrite(PIN_LED_MOUTH_1, pwm_from_brightness(brightness5, scaleMT, baseMT));

  // Update loop timer & time update:
  // Keep the animation updating smoothly at a constant 'realtime' speed by using a simple frame-to-frame timer
  const float dt = SyncFrameTick(10); // Tick @ 10ms if possible
  cur_time += dt * kAnimationSpeed;
  if (cur_time > kTimeWrap)
    cur_time = -kTimeWrap;

  startupFader = min(1.0f, startupFader + dt);
}

//----------------------------------------------------------------------------

float SyncFrameTick(int targetMs)
{
  const int       kDefaultUpdateMsOnInternalOverflow = 10; // When u32 overflows (after ~50 days), default to 10ms step
  unsigned long   curMs = 0;
  int             updateMs = 0;
  do
  {
    curMs = millis();
    updateMs = ((curMs >= prevFrameMs) ? (curMs - prevFrameMs) : kDefaultUpdateMsOnInternalOverflow);
    if (updateMs < targetMs)
      delay(targetMs - updateMs);
  } while (updateMs < targetMs);

  prevFrameMs = curMs;
  
  return updateMs * 0.001f;
}

//----------------------------------------------------------------------------
//
//  Bluetooth command reader
//
//----------------------------------------------------------------------------

char  serialBuffer[32];
int   serialBufferPos = 0;

//----------------------------------------------------------------------------

void  ReadSerialInput()
{
  const int kMaxSerialBufferCapacity = sizeof(serialBuffer) / sizeof(serialBuffer[0]) - 1;  // commands can be 31 chars long at most
  int       itCount = 0;
  while (Serial.available() > 0 &&
         itCount < 5) // Avoid freezing entirely if for whatever reason some app connected to us and sent us 100Kb of text
  {
    ++itCount;
    int   i = serialBufferPos;
    while (Serial.available() > 0)
    {
      char c = Serial.read();
      if (c == '\n' || c == ';')  // end of command
      {
        if (i > kMaxSerialBufferCapacity)
          i = kMaxSerialBufferCapacity;
        serialBuffer[i] = '\0';
        i = 0;
        RunCommand(serialBuffer);
        break;
      }

      // Eat the rest of the entire command even if it overflows the buffer, just truncate it.
      if (i < kMaxSerialBufferCapacity)
        serialBuffer[i++] = c;
    }

    serialBufferPos = i;
  }
}

//----------------------------------------------------------------------------

#define STR_EQUALS(__str, __pattern)  (!strcmp(__str, __pattern))
#define STR_STARTS_WITH(__str, __pattern)  (!strncmp(__str, __pattern, sizeof(__pattern)-1))

void RunCommand(char cmd[])
{
  if (STR_EQUALS(cmd, "off"))
  {
    // Turn everything off
    ledFactor_Soulstone = 0.0f;
    ledFactor_Eyes = 0.0f;
    ledFactor_Mouth = 0.0f;
  }
  else if (STR_EQUALS(cmd, "rst"))
  {
    // Reset everything to default
    ledFactor_Soulstone = 1.0f;
    ledFactor_Eyes = 1.0f;
    ledFactor_Mouth = 1.0f;
    ledFactor_BaseVar = 1.0f;
  }
  else if (STR_STARTS_WITH(cmd, "s="))
  {
    // Set soulstone intensity
    int v = atoi(cmd + 2);
    ledFactor_Soulstone = clamp(v / 255.0f, 0.0f, 1.0f);
  }
  else if (STR_STARTS_WITH(cmd, "e="))
  {
    // Set eyes intensity
    int v = atoi(cmd + 2);
    ledFactor_Eyes = clamp(v / 255.0f, 0.0f, 1.0f);
  }
  else if (STR_STARTS_WITH(cmd, "m="))
  {
    // Set mouth intensity
    int v = atoi(cmd + 2);
    ledFactor_Mouth = clamp(v / 255.0f, 0.0f, 1.0f);
  }
  else if (STR_STARTS_WITH(cmd, "v="))
  {
    // Set overall sinewave variation factor
    int v = atoi(cmd + 2);
    ledFactor_BaseVar = clamp(v / 255.0f, 0.0f, 1.0f);
  }
  else if (STR_STARTS_WITH(cmd, "b="))
  {
    // Set baseline sync factor
    int v = atoi(cmd + 2);
    ledFactor_BaseSync = clamp(v / 255.0f, 0.0f, 1.0f);
  }
}

//----------------------------------------------------------------------------
//
//  EEPROM management
//
//----------------------------------------------------------------------------

unsigned long eeprom_compute_sum()
{
  unsigned long sum = 0;
  for (int i = 0, stop = EEPROM.length() - 4; i < stop; i++)
    sum += EEPROM[i];
  return sum;
}

unsigned long eeprom_read_sum()
{
  unsigned long sum = 0;
  EEPROM.get(EEPROM.length() - 4, sum);
  return sum;
}

void  eeprom_reset()
{
  for (int i = 0, stop = EEPROM.length() / 4; i < stop; i++)
    EEPROM.update(i, 0);
}
