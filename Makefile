build: debug

unit-test:
	./gradlew testDebugUnitTest

# NB this requires a running emulator.
ui-test:
	./gradlew connectedDebugAndroidTest

debug:
	./gradlew assembleDebug

debug-install:
	adb install -r app/build/outputs/apk/debug/app-debug.apk

uninstall:
	adb uninstall com.example.helloworldkotlinandroid

release: HelloWorldKotlinAndroid.keystore
	./gradlew clean assembleRelease

release-install:
	adb install -r app/build/outputs/apk/release/app-release.apk

clean:
	./gradlew clean

HelloWorldKotlinAndroid.keystore:
	@echo 'creating signing keystore...'
	keytool -genkey -v -storepass password -keypass password -keystore HelloWorldKotlinAndroid.keystore -alias helloworldkotlinandroid -dname 'CN=helloworldkotlinandroid,O=example.com' -keyalg RSA -keysize 2048 -validity 10000

retrace:
	cmd /c "%ANDROID_HOME%/tools/proguard/bin/retrace app/build/outputs/mapping/release/mapping.txt stacktrace.txt"
