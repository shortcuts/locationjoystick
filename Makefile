stop:
	pkill -f gradlew || true
	pkill -f "gradle" || true
	pkill -f ktlint || true
	pkill -f "adb logcat" || true

clean:
	./gradlew clean
	rm -rf .gradle
	rm -rf build
	find . -name "build" -type d -not -path "*/.git/*" | xargs rm -rf
	find . -name ".gradle" -type d -not -path "*/.git/*" | xargs rm -rf

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

test:
	./gradlew testRelease

coverage:
	./gradlew koverHtmlReport koverXmlReport

coverage-open:
	open build/reports/kover/html/index.html

screenshot:
	./scripts/screenshot-gallery.sh
