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

.PHONY: build
build:
	./gradlew assembleRelease

bundle:
	./gradlew bundleRelease

format:
	ktlint -F '**/*.{kt,kts}' '!**/build/**'

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
	./scripts/screenshot-gallery.sh --output docs/wiki/screenshots

wiki-serve:
	lsof -ti:8080 | xargs kill -9 || true
	python3 -m http.server 8080 --directory docs/wiki

SMOKE_TEST_CLASS = $(subst /,.,$(patsubst app/src/androidTest/kotlin/%,%,$(patsubst %.kt,%,$(TEST_FILE))))

smoke-test:
	./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.locationjoystick.app.smoke

smoke-test-one:
	./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=$(SMOKE_TEST_CLASS)
