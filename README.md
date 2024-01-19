# Parts is Parts

![Just Build](https://github.com/EAGrahamJr/kobots-parts/actions/workflows/build.yaml/badge.svg) ![Kotlin](https://badgen.net/badge/Kotlin/1.9.0/purple) ![Java](https://badgen.net/badge/Java/17/orange) ![Apache License](https://badgen.net/github/license/EAGrahamJr/kobots-devices)

Contains basic application construction elements that are being used in my various Kobots-related projects. This became desirable after it became apparent that these elements were being repeated across the projects via copy/pasta with tweaks.

- Generic application "junk"
- Abstractions to orchestrate physical device interactions
  - Includes movements and the human stuff
- A simplified event-bus for in-process communication
- Wrappers around MQTT for external communications

There are three main sections.

- [Actuators and Movements](Movements.md)
- [Event Bus](EventBus.md)
- [Home Assistant](HomeAssistant.md)

Javadocs are published at the [GitHub Pages](https://eagrahamjr.github.io/kobots-parts/) for this project.



## Other Stuff

- [`NeoKeyHandler`](src/main/kotlin/crackers/kobots/parts/app/io/NeoKeyHandler.kt) is a "wrapper" around handling a rotating "menu" of actions
  - use with the `NeoKeyMenu` class to get complete key-press handling and automatic invocation of actions
- [`StatusColumnDisplay`](src/main/kotlin/crackers/kobots/parts/app/io/StatusColumnDisplay.kt) displays numbers in named columns (e.g. for OLED and TFT displays)
    - produces a small image of the columns
- [`SmallMenuDisplay`](src/main/kotlin/crackers/kobots/parts/app/io/SmallMenuDisplay.kt) that works with the `NeoKeyMenu` stuff
- [`AppCommon`](src/main/kotlin/crackers/kobots/parts/app/AppCommon.kt) after I did the same :poop: 3 times
  - see [Appendix A](#appendix-a-configuration-reference) for configuration reference
- [`KobotsMQTT`](src/main/kotlin/crackers/kobots/parts/mqtt/KobotsMQTT.kt) for a common **Qos 0** messaging
    - don't want stuff happening 3 hours later on a late message delivery 

## Appendix A: Configuration reference

```hocon
{
  ha: {
    token: "HomeAssitant Credentials Token"
    server: "127.0.0.1"
    port: 8123
  }
  mqtt.broker = "tcp://127.0.0.1:1883"
}

```
