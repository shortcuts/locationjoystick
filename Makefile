build:
	./gradlew assembleRelease

format:
	ktlint --format

lint:
	./gradlew lintRelease

install-on-phone:
	adb uninstall com.locationjoystick.app || true && ./gradlew installDebug

start-on-phone:
	adb shell am start -n com.locationjoystick.app/.MainActivity

tail-log-on-phone:
	adb logcat --pid=$(adb shell pidof com.locationjoystick.app)
