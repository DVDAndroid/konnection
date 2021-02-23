# konnection

A Kotlin Multiplatform Mobile library to emit Network Connection status.

![Sample Android](art/sample_android.gif) ![Sample iOS](art/sample_ios.gif)

## Add to your project

### build.gradle
```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation "dev.tmapps:konnection:1.0.0"
}
```

### build.gradle.kts
```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.tmapps:konnection:1.0.0")
}
```

### Multiplatform (Common, Android, iOS)
In common code that should get compiled for different platforms, you can add dependency right to the `commonMain` source set:
```groovy
commonMain {
    dependencies {
        // Works as common dependency as well as the platform one
        implementation("dev.tmapps:konnection:1.0.0")
    }
}
```

## Usage

### on Android
```kotlin
// instantiate the Konnection class with a android.content.Context
val konnection = Konnection(context)

// get the immediate connection state
val hasNetworkConnection = konnection.isConnected()

// observes current NetworkConnection (WIFI, MOBILE, NONE) state.
konnection.observeConnection()
    .collect { state -> ... }
```

### on iOS

```kotlin
// instantiate the Konnection class
val konnection = Konnection()

// get the immediate connection state
val hasNetworkConnection = konnection.isConnected()

// emits current NetworkConnection (WIFI, MOBILE, NONE) state
fun networkConnectionObservation(callback: (NetworkConnection) -> Unit) {
    konnection.observeConnection()
        .onEach { callback(it) }
        .launchIn(...)
}

// stops the publishing of connection state.
// this is necessary to stop and clear the internal SCNetworkReachability references
// and free the created pointers on native heap memory  
konnection.stop()
```

## License

    Copyright 2021 TMApps
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.