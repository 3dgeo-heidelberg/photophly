# PhotoPhly connector App
This repository houses the source code for the **PhotoPhly connector** android app. The app
acts as a bridge between the remote controller of a DJI drone and an (external) web service.

## Requirements
For compilation, you need to create a DJI Developer Key (https://developer.dji.com/) and insert it into
`app/src/main/AndroidManifest.xml`. Building has been tested using Gradle 7.0.2 in Android Studio
and for Android SDK Platform 30.

## How to use the app
When connected to a DJI remote control, the app starts a web service available on port `9000` (default).
The following endpoints exist:

| Endpoint             | Method | Description, Parameters                                                                                                                                  |
|----------------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| /hello               | GET    | returns "Hello World"                                                                                                                                    |
| /cgi/getState        | GET    | returns a JSON with the state of the drone (lat, lon, height, yaw, pitch, roll, mode, battery, gimbal)                                                   |
| /cgi/setState        | POST   | accepts a JSON to set the state (roll, pitch, yaw, throttle, gimbal)                                                                                     |
| /cgi/takePhoto       | PUT    | sets the camera mode to "SHOOT_PHOTO", takes a single photo                                                                                              |
| /cgi/takeSinglePhoto | PUT    | takes a single photo (assumes that camera mode is already set)                                                                                           |
| /cgi/startInterval   | PUT    | starts taking photos after setting the camera mode to "INTERVAL" for the first photo. Accepts parameter "interval", which is the interval between photos |
| /cgi/stopInterval    | PUT    | stops the automatic photo shooting                                                                                                                       |

## License and liability
PhotoPhly is licensed under the MIT License:

```
Copyright 2022 Lukas Winiwarter

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

Please note that you as the **operator** and **pilot** of the drone are **directly and solely responsible** for any damages created by the usage of this app. By inserting a DJI Developer Key into the AndroidManifest.xml file you agree to this license.

## Emergencies
In the case of an emergency, we suggest the following protocol:
1) Try to stop the "VirtualStick" mode on the app. You should have immediate throttle control over the drone (while the camera image is still transmitted).
2) If that does not work, the app crashes or freezes, unplug the remote control from the mobile device. You will have immediate throttle control.
3) Reconnect the drone and start the DJI app to return to home.