# Diablo Head Sculpt from Diablo 2 Resurrected

Diablo head from D2R, cast in resin, and containing a lighting circuit to light up the soulstone, eyes, and mouth.
Mounted on a wooden support to be hung up on a wall.

This repository contains things related to the various electronics & app parts:
- Various source files to compile the program for the Arduino & firmware for the ESP8266.
- Android app used to drive the bluetooth module.
- EasyEDA & gerber files for the custom PCBs


Main sculpting references:
- https://www.artstation.com/artwork/VgrwZn


## Youtube video teaser

[![D2 Head sculpt teaser](https://img.youtube.com/vi/fM9Wi7bSkBk/0.jpg)](https://www.youtube.com/watch?v=fM9Wi7bSkBk)


# Current states
Sculpt not yet finished. Electronics more or less final, will wait for first casting & initial assembly before ordering & assembling the Mk5 board, and doing another potential iteration @ Mk6 if further adjustments need to be made.

Update on controller circuit: With mk6, will try to use an ESP-12E instead of an arduino + bluetooth module + ESP-1
The ESP-12E has enough pins to drive the 5 LED rails, and would allow to remove the need for any inter-module communication.
The wifi module can behave as an access point initially to allow configuring wifi access, then connect to the local wifi.
No need for bluetooth, what we really want is wifi control. Plus the serial communication between the arduino & BT module is pretty shite.

## Current sculpt state
- [x] Final eyes placeholders, threaded, will receive centering rods for casting
- [x] Final face proportions
- [x] Final main horns proportions
- [ ] Final top of head proportions & fixup top horns (~1d)
- [ ] Final neck proportions (~4h)
- [ ] Fixup main horns front bulk (~1h)
- [ ] Inside of mouth & teeth (~1.5d)
- [ ] overall final detailing (~4d)
- [ ] ... Moldmaking ...

![Latest Sculpt State](Latest_Sculpt.jpg)

## Current Hardware state
Mk4, Waiting for first casting & initial assembly to iterate further

![Latest Assembly](PCB/Latest_Assembly.jpg)

![Latest Board](PCB/Latest_Board.jpg)

## Current PCB layout
Mk6, Waiting for first casting & initial assembly to iterate further

![PCB](PCB/Latest_PCB.png)
