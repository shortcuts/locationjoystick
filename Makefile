.PHONY: stop clean build bundle format lint install-on-phone reinstall-on-phone start-on-phone tail-log-on-phone \
	test coverage coverage-open screenshot screenshot-playstore accessibility-video wiki-changelog wiki-serve \
	smoke-test smoke-test-one reconnect-matrix reconnect-auto reconnect-already-on reconnect-summary reconnect-selfcheck tap-smoke tap-smoke-selfcheck

stop:
	pkill -f gradlew || true
	pkill -f "gradle" || true
	pkill -f ktlint || true
	pkill -f "adb logcat" || true

clean:
	rm -rf ~/.gradle/caches
	rm -rf ~/.gradle/.tmp
	rm -rf ~/.gradle/daemon
	rm -rf ~/.gradle/configuration-cache
	rm -rf .gradle
	rm -rf build
	find . -name "build" -type d -not -path "*/.git/*" | xargs rm -rf
	find . -name ".gradle" -type d -not -path "*/.git/*" | xargs rm -rf

build:
	./gradlew assembleRelease

bundle:
	./gradlew bundleRelease

format:
	./gradlew ktlintFormat

lint:
	./gradlew lintRelease

install-on-phone:
	./gradlew installDebug

reinstall-on-phone:
	adb uninstall com.locationjoystick.app || true && ./gradlew installDebug

start-on-phone:
	adb shell am start -n com.locationjoystick.app/com.locationjoystick.app.MainActivity

tail-log-on-phone:
	adb logcat --pid=$(adb shell pidof com.locationjoystick.app)

test:
	./gradlew testRelease

coverage:
	./gradlew koverHtmlReport koverXmlReport

coverage-open:
	open build/reports/kover/html/index.html

screenshot:
	./scripts/screenshot-gallery.sh --auto

screenshot-playstore:
	./scripts/screenshot-gallery.sh --marketing-only

accessibility-video:
	./scripts/playstore-foreground-location-video.sh --flow accessibility

wiki-changelog:
	python3 scripts/generate-changelog.py

wiki-serve:
	lsof -ti:8080 | xargs kill -9 || true
	python3 -m http.server 8080 --directory docs/wiki

SMOKE_TEST_CLASS = $(subst /,.,$(patsubst app/src/androidTest/kotlin/%,%,$(patsubst %.kt,%,$(TEST_FILE))))

smoke-test:
	./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.locationjoystick.app.smoke

smoke-test-one:
	./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=$(SMOKE_TEST_CLASS)

# Bluetooth reconnect tap matrix (docs/features/bluetooth-reconnect-tap.md).
# make reconnect-auto CYCLES=10   (SERIAL, DEVICE, MAC, BLOCK optional: script picks)
RECONNECT = python3 scripts/reconnect_matrix.py
CYCLES ?= 100
DEVICE_ARGS = $(if $(SERIAL),--serial $(SERIAL)) $(if $(DEVICE),--device-slug $(DEVICE)) $(if $(MAC),--mac-suffix $(MAC)) $(if $(BLOCK),--block $(BLOCK))

reconnect-auto:
	$(RECONNECT) auto $(DEVICE_ARGS) --cycles $(CYCLES)

reconnect-already-on:
	$(RECONNECT) already-on $(DEVICE_ARGS)

# Full unattended proof on one device, release cadence (60 s loop): release build signed with the
# local debug keystore, 12 already-on checks, auto blocks a-e x 20 cycles, then the summary.
# Blocks run with a leading '-' so a crashed block does not skip the rest; failed cycles are rows in
# scripts/reconnect-matrix/cycles.jsonl and are listed by the summary. Takes hours.
MATRIX_SERIAL ?= 0A061FDD4000JF
MAC_SUFFIX ?= D3:24
DEVICE_SLUG ?= pixel5
MATRIX_ARGS = --serial $(MATRIX_SERIAL) --mac-suffix $(MAC_SUFFIX) --device-slug $(DEVICE_SLUG)

reconnect-matrix:
	ANDROID_SERIAL=$(MATRIX_SERIAL) KEYSTORE_PATH=$$HOME/.android/debug.keystore STORE_PASSWORD=android \
		KEY_ALIAS=androiddebugkey KEY_PASSWORD=android ./gradlew installRelease
	@if adb -s $(MATRIX_SERIAL) shell dumpsys package com.locationjoystick.app | grep -q 'flags=\[.*DEBUGGABLE'; then \
		echo "ERROR: installed build is debuggable (10 s loop), not release cadence"; exit 1; fi
	-$(RECONNECT) already-on $(MATRIX_ARGS) --checks 12
	-$(RECONNECT) auto $(MATRIX_ARGS) --block a --cycles 20
	-$(RECONNECT) auto $(MATRIX_ARGS) --block b --cycles 20
	-$(RECONNECT) auto $(MATRIX_ARGS) --block c --cycles 20
	-$(RECONNECT) auto $(MATRIX_ARGS) --block d --cycles 20
	-$(RECONNECT) auto $(MATRIX_ARGS) --block e --cycles 20
	$(RECONNECT) summary
	@echo "Failed cycles: rows with \"pass\": false in scripts/reconnect-matrix/cycles.jsonl"

reconnect-summary:
	$(RECONNECT) summary

reconnect-selfcheck:
	python3 scripts/test_reconnect_matrix.py

# Tap-to-walk global smoke gate (docs/testing.md). Throwaway game account only.
# make tap-smoke SERIAL=<adb serial> ONLY="1 2" SCENARIOS=all SPEED_PROFILE=walk   (needs LJ_CONTROL_API_KEY, LJ_CONTROL_API_HOST)
tap-smoke:
	scripts/registration-bench/.venv/bin/python scripts/tap_smoke.py --ack-throwaway-account \
		$(if $(SERIAL),--serial $(SERIAL)) $(if $(ONLY),--only $(ONLY)) $(if $(SCENARIOS),--scenarios $(SCENARIOS)) \
		$(if $(SPEED_PROFILE),--speed-profile $(SPEED_PROFILE))

tap-smoke-selfcheck:
	python3 scripts/test_tap_smoke.py
