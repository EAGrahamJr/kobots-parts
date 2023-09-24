# Parts is Parts

Contains basic application construction elements that are being used in my various Kobots-related projects. This became desirable after it became apparent that these elements were being repeated across the projects via copy/pasta with tweaks.

- Generic application "junk"
- Abstractions to orchestrate physical device interactions
  - Includes movements and the human stuff
- A simplified event-bus for in-process communication
- Wrappers around MQTT for external communications

There are two main sections.

- [Actuators and Movements](Movements.md)
- [Event Bus](EventBus.md)

## Other Stuff

- [`NeoKeyHandler`](src/main/kotlin/crackers/kobots/parts/app/io/NeoKeyHandler.kt) is a "wrapper" around handling a rotating "menu" of actions
  - use with the `NeoKeyMenu` class to get complete key-press handling and automatic invocation of actions
- [`StatusColumnDisplay`](src/main/kotlin/crackers/kobots/app/io/StatusColumnDisplay.kt) displays numbers in named columns (e.g. for OLED and TFT displays)
