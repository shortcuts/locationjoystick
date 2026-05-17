# Known Issues & Backlog

## Backlog

### about page information

should leverage the AppConstants.kt AppInfo URL for linking to github and reporting a bug. The AGENTS.md "Info / About Page" section properly describes it.

### create route from map

the map overlay should display the same "map screen" features such as "pick from favorite", in order to quickly jump to a favorite location to start a route, and the "center on location" icon.

### transfer/import from QR

error when trying to import from a scanned QR 

05-17 23:20:49.354  9418  9418 D CameraOrientationUtil: getRelativeImageRotation: destRotationDegrees=0, sourceRotationDegrees=90, isOppositeFacing=true, result=90
05-17 23:20:49.540  9418  9418 D StreamStateObserver: Update Preview stream state to STREAMING
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: Scan error
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: org.json.JSONException: Value 11541738 of type java.lang.Integer cannot be converted to JSONObject
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at org.json.JSON.typeMismatch(JSON.java:112)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at org.json.JSONObject.<init>(JSONObject.java:172)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at org.json.JSONObject.<init>(JSONObject.java:185)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at com.locationjoystick.feature.settings.impl.ZxingImageAnalyzer.parseEnvelope(ZxingImageAnalyzer.kt:62)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at com.locationjoystick.feature.settings.impl.ZxingImageAnalyzer.analyze(ZxingImageAnalyzer.kt:48)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at androidx.camera.core.ImageAnalysis.lambda$setAnalyzer$3(ImageAnalysis.java:559)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at androidx.camera.core.ImageAnalysis$$ExternalSyntheticLambda2.analyze(D8$$SyntheticClass:0)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at androidx.camera.core.ImageAnalysisAbstractAnalyzer.lambda$analyzeImage$0$androidx-camera-core-ImageAnalysisAbstractAnalyzer(ImageAnalysisAbstractAnalyzer.java:286)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at androidx.camera.core.ImageAnalysisAbstractAnalyzer$$ExternalSyntheticLambda0.run(D8$$SyntheticClass:0)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1154)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:652)
05-17 23:20:55.104  9418 11190 W ZxingImageAnalyzer: 	at java.lang.Thread.run(Thread.java:1564)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: Scan error
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: org.json.JSONException: Value 11812487 of type java.lang.Integer cannot be converted to JSONObject
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at org.json.JSON.typeMismatch(JSON.java:112)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at org.json.JSONObject.<init>(JSONObject.java:172)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at org.json.JSONObject.<init>(JSONObject.java:185)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at com.locationjoystick.feature.settings.impl.ZxingImageAnalyzer.parseEnvelope(ZxingImageAnalyzer.kt:62)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at com.locationjoystick.feature.settings.impl.ZxingImageAnalyzer.analyze(ZxingImageAnalyzer.kt:48)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at androidx.camera.core.ImageAnalysis.lambda$setAnalyzer$3(ImageAnalysis.java:559)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at androidx.camera.core.ImageAnalysis$$ExternalSyntheticLambda2.analyze(D8$$SyntheticClass:0)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at androidx.camera.core.ImageAnalysisAbstractAnalyzer.lambda$analyzeImage$0$androidx-camera-core-ImageAnalysisAbstractAnalyzer(ImageAnalysisAbstractAnalyzer.java:286)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at androidx.camera.core.ImageAnalysisAbstractAnalyzer$$ExternalSyntheticLambda0.run(D8$$SyntheticClass:0)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1154)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:652)
05-17 23:20:55.780  9418 11190 W ZxingImageAnalyzer: 	at java.lang.Thread.run(Thread.java:1564)
