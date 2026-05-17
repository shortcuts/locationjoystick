# Known Issues & Backlog

## Backlog

### create route from map

the map overlay should display the same "map screen" features such as "pick from favorite", in order to quickly jump to a favorite location to start a route, and the "center on location" icon.

### import from QR does nothing

05-17 23:55:59.648 19881 19881 D CameraOrientationUtil: getRelativeImageRotation: destRotationDegrees=0, sourceRotationDegrees=90, isOppositeFacing=true, result=90
05-17 23:55:59.648 19881 19881 D CameraOrientationUtil: getRelativeImageRotation: destRotationDegrees=0, sourceRotationDegrees=90, isOppositeFacing=true, result=90
05-17 23:55:59.651 19881 19881 D DynamicRangeResolver: Resolved dynamic range for use case androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3 to no compatible HDR dynamic ranges.
05-17 23:55:59.651 19881 19881 D DynamicRangeResolver: DynamicRange@908e7a0{encoding=UNSPECIFIED, bitDepth=0}
05-17 23:55:59.651 19881 19881 D DynamicRangeResolver: ->
05-17 23:55:59.651 19881 19881 D DynamicRangeResolver: DynamicRange@c3ae8a3{encoding=SDR, bitDepth=8}
05-17 23:55:59.653 19881 19881 D CameraOrientationUtil: getRelativeImageRotation: destRotationDegrees=0, sourceRotationDegrees=90, isOppositeFacing=true, result=90
05-17 23:55:59.654 19881 19881 D DeferrableSurface: Surface created[total_surfaces=1, used_surfaces=0](androidx.camera.core.processing.SurfaceEdge$SettableSurface@2ef72a5}
05-17 23:55:59.654 19881 19881 D DeferrableSurface: Surface created[total_surfaces=2, used_surfaces=0](androidx.camera.core.SurfaceRequest$2@8099421}
05-17 23:55:59.654 19881 19881 D DeferrableSurface: New surface in use[total_surfaces=2, used_surfaces=1](androidx.camera.core.SurfaceRequest$2@8099421}
05-17 23:55:59.654 19881 19881 D DeferrableSurface: use count+1, useCount=1 androidx.camera.core.SurfaceRequest$2@8099421
05-17 23:55:59.654 19881 19881 D CameraOrientationUtil: getRelativeImageRotation: destRotationDegrees=0, sourceRotationDegrees=90, isOppositeFacing=true, result=90
05-17 23:55:59.656 19881 19881 D CameraOrientationUtil: getRelativeImageRotation: destRotationDegrees=0, sourceRotationDegrees=90, isOppositeFacing=true, result=90
05-17 23:55:59.656 19881 19881 D CameraOrientationUtil: getRelativeImageRotation: destRotationDegrees=0, sourceRotationDegrees=90, isOppositeFacing=true, result=90
05-17 23:55:59.657 19881 19881 D DeferrableSurface: Surface created[total_surfaces=3, used_surfaces=1](androidx.camera.core.impl.ImmediateSurface@d5d2ad2}
05-17 23:55:59.658 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Use case androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905 ACTIVE
05-17 23:55:59.658 19881 20481 D UseCaseAttachState: Active and attached use case: [] for camera: 0
05-17 23:55:59.659 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Use case androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534 ACTIVE
05-17 23:55:59.659 19881 20481 D UseCaseAttachState: Active and attached use case: [] for camera: 0
05-17 23:55:59.660 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Use cases [androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905, androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534] now ATTACHED
05-17 23:55:59.661 19881 20481 D UseCaseAttachState: All use case: [androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905, androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534] for camera: 0
05-17 23:55:59.662 19881 20481 D UseCaseAttachState: Active and attached use case: [androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905, androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534] for camera: 0
05-17 23:55:59.664 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Resetting Capture Session
05-17 23:55:59.664 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Releasing session in state INITIALIZED
05-17 23:55:59.664 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Attempting to force open the camera.
05-17 23:55:59.665 19881 20481 D CameraStateRegistry: tryOpenCamera(Camera@c7baa41[id=0]) [Available Cameras: 1, Already Open: false (Previous state: CLOSED)] --> SUCCESS
05-17 23:55:59.665 19881 20481 D CameraStateRegistry: Recalculating open cameras:
05-17 23:55:59.665 19881 20481 D CameraStateRegistry: Camera                                       State
05-17 23:55:59.665 19881 20481 D CameraStateRegistry: -------------------------------------------------------------------
05-17 23:55:59.665 19881 20481 D CameraStateRegistry: Camera@c7baa41[id=0]                         OPENING
05-17 23:55:59.665 19881 20481 D CameraStateRegistry: Camera@fbfc940[id=1]                         UNKNOWN
05-17 23:55:59.665 19881 20481 D CameraStateRegistry: -------------------------------------------------------------------
05-17 23:55:59.665 19881 20481 D CameraStateRegistry: Open count: 1 (Max allowed: 1)
05-17 23:55:59.665 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Opening camera.
05-17 23:55:59.665 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Transitioning camera internal state: INITIALIZED --> OPENING
05-17 23:55:59.665 19881 20481 D CameraStateMachine: New public camera state CameraState{type=OPENING, error=null} from OPENING and null
05-17 23:55:59.665 19881 20481 D CameraStateMachine: Publishing new public camera state CameraState{type=OPENING, error=null}
05-17 23:55:59.666 19881 20481 D UseCaseAttachState: All use case: [androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905, androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534] for camera: 0
05-17 23:55:59.670 19881 19881 D PreviewView: Surface requested by Preview.
05-17 23:55:59.672 19881 19881 D PreviewView: Preview transformation info updated. TransformationInfo{getCropRect=Rect(0, 0 - 1600, 1200), getRotationDegrees=90, getTargetRotation=-1, hasCameraTransform=true, getSensorToBufferTransform=Matrix{[1.0, 0.0, 0.0][0.0, 1.0, 0.0][0.0, 0.0, 1.0]}, getMirroring=false}
05-17 23:55:59.672 19881 19881 D PreviewTransform: Transformation info set: TransformationInfo{getCropRect=Rect(0, 0 - 1600, 1200), getRotationDegrees=90, getTargetRotation=-1, hasCameraTransform=true, getSensorToBufferTransform=Matrix{[1.0, 0.0, 0.0][0.0, 1.0, 0.0][0.0, 0.0, 1.0]}, getMirroring=false} 1600x1200 false
05-17 23:55:59.672 19881 19881 D CameraOrientationUtil: getRelativeImageRotation: destRotationDegrees=0, sourceRotationDegrees=90, isOppositeFacing=true, result=90
05-17 23:55:59.680 19881 19881 D TextureViewImpl: SurfaceTexture available. Size: 1600x1200
05-17 23:55:59.680 19881 19881 D TextureViewImpl: Surface set on Preview.
05-17 23:55:59.682 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Use case androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905 ACTIVE
05-17 23:55:59.683 19881 20481 D UseCaseAttachState: Active and attached use case: [androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905, androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534] for camera: 0
05-17 23:55:59.684 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Use case androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534 ACTIVE
05-17 23:55:59.684 19881 20481 D UseCaseAttachState: Active and attached use case: [androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905, androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534] for camera: 0
05-17 23:55:59.685 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} CameraDevice.onOpened()
05-17 23:55:59.685 19881 20481 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Transitioning camera internal state: OPENING --> OPENED
05-17 23:55:59.685 19881 20481 D CameraStateRegistry: Recalculating open cameras:
05-17 23:55:59.685 19881 20481 D CameraStateRegistry: Camera                                       State
05-17 23:55:59.685 19881 20481 D CameraStateRegistry: -------------------------------------------------------------------
05-17 23:55:59.685 19881 20481 D CameraStateRegistry: Camera@c7baa41[id=0]                         OPEN
05-17 23:55:59.685 19881 20481 D CameraStateRegistry: Camera@fbfc940[id=1]                         UNKNOWN
05-17 23:55:59.685 19881 20481 D CameraStateRegistry: -------------------------------------------------------------------
05-17 23:55:59.685 19881 20481 D CameraStateRegistry: Open count: 1 (Max allowed: 1)
05-17 23:55:59.685 19881 20481 D CameraStateMachine: New public camera state CameraState{type=OPEN, error=null} from OPEN and null
05-17 23:55:59.686 19881 20481 D CameraStateMachine: Publishing new public camera state CameraState{type=OPEN, error=null}
05-17 23:55:59.686 19881 20481 D UseCaseAttachState: All use case: [androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905, androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534] for camera: 0
05-17 23:55:59.687 19881 20481 D UseCaseAttachState: Active and attached use case: [androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905, androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534] for camera: 0
05-17 23:55:59.688 19881 20481 D SyncCaptureSessionBase: [androidx.camera.camera2.internal.SynchronizedCaptureSessionBaseImpl@73c89da] getSurface...done
05-17 23:55:59.688 19881 20481 D CaptureSession: Opening capture session.
05-17 23:55:59.689 19881 20481 D DeferrableSurface: use count+1, useCount=2 androidx.camera.core.SurfaceRequest$2@8099421
05-17 23:55:59.689 19881 20481 D DeferrableSurface: New surface in use[total_surfaces=3, used_surfaces=2](androidx.camera.core.impl.ImmediateSurface@d5d2ad2}
05-17 23:55:59.689 19881 20481 D DeferrableSurface: use count+1, useCount=1 androidx.camera.core.impl.ImmediateSurface@d5d2ad2
05-17 23:55:59.938 19881 20481 D CaptureSession: Attempting to send capture request onConfigured
05-17 23:55:59.938 19881 20481 D CaptureSession: Issuing request for session.
05-17 23:55:59.939 19881 20481 D Camera2CaptureRequestBuilder: createCaptureRequest
05-17 23:55:59.940 19881 20481 D CaptureSession: CameraCaptureSession.onConfigured() mState=OPENED
05-17 23:55:59.940 19881 20481 D CaptureSession: CameraCaptureSession.onReady() OPENED
05-17 23:55:59.968 19881 19881 D CameraOrientationUtil: getRelativeImageRotation: destRotationDegrees=0, sourceRotationDegrees=90, isOppositeFacing=true, result=90
05-17 23:56:00.130 19881 19881 D StreamStateObserver: Update Preview stream state to STREAMING
05-17 23:56:02.545 19881 19887 I ionjoystick.app: Background concurrent copying GC freed 310KB AllocSpace bytes, 46(26MB) LOS objects, 75% free, 11MB/47MB, paused 152us,26us total 101.340ms
05-17 23:56:05.810 19881 19887 I ionjoystick.app: Background concurrent copying GC freed 395KB AllocSpace bytes, 66(38MB) LOS objects, 75% free, 14MB/56MB, paused 95us,56us total 101.657ms
05-17 23:56:17.483 19881 20484 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Use cases [androidx.camera.core.Preview-b15e5dd4-678e-4acf-a502-caa43f417ad3116764905, androidx.camera.core.ImageAnalysis-abaa3199-32c9-4e64-911f-a106dcc3a856263815534] now DETACHED for camera
05-17 23:56:17.483 19881 20484 D UseCaseAttachState: All use case: [] for camera: 0
05-17 23:56:17.483 19881 20484 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Resetting Capture Session
05-17 23:56:17.484 19881 20484 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Releasing session in state OPENED
05-17 23:56:17.484 19881 20484 D UseCaseAttachState: Active and attached use case: [] for camera: 0
05-17 23:56:17.485 19881 20484 D UseCaseAttachState: Active and attached use case: [] for camera: 0
05-17 23:56:17.485 19881 20484 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Closing camera.
05-17 23:56:17.486 19881 20484 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Transitioning camera internal state: OPENED --> CLOSING
05-17 23:56:17.486 19881 20484 D CameraStateRegistry: Recalculating open cameras:
05-17 23:56:17.486 19881 20484 D CameraStateRegistry: Camera                                       State
05-17 23:56:17.486 19881 20484 D CameraStateRegistry: -------------------------------------------------------------------
05-17 23:56:17.486 19881 20484 D CameraStateRegistry: Camera@c7baa41[id=0]                         CLOSING
05-17 23:56:17.486 19881 20484 D CameraStateRegistry: Camera@fbfc940[id=1]                         UNKNOWN
05-17 23:56:17.486 19881 20484 D CameraStateRegistry: -------------------------------------------------------------------
05-17 23:56:17.486 19881 20484 D CameraStateRegistry: Open count: 1 (Max allowed: 1)
05-17 23:56:17.486 19881 20484 D CameraStateMachine: New public camera state CameraState{type=CLOSING, error=null} from CLOSING and null
05-17 23:56:17.486 19881 20484 D CameraStateMachine: Publishing new public camera state CameraState{type=CLOSING, error=null}
05-17 23:56:17.486 19881 20484 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Resetting Capture Session
05-17 23:56:17.486 19881 20484 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Releasing session in state CLOSING
05-17 23:56:17.487 19881 20484 D CaptureSession: onSessionFinished()
05-17 23:56:17.611 19881 19881 D StreamStateObserver: Update Preview stream state to IDLE
05-17 23:56:17.662 19881 19881 D DeferrableSurface: surface closed,  useCount=2 closed=true androidx.camera.core.SurfaceRequest$2@8099421
05-17 23:56:17.662 19881 19881 D DeferrableSurface: surface closed,  useCount=0 closed=true androidx.camera.core.processing.SurfaceEdge$SettableSurface@2ef72a5
05-17 23:56:17.662 19881 19881 D DeferrableSurface: Surface terminated[total_surfaces=2, used_surfaces=2](androidx.camera.core.processing.SurfaceEdge$SettableSurface@2ef72a5}
05-17 23:56:17.662 19881 19881 D DeferrableSurface: use count-1,  useCount=1 closed=true androidx.camera.core.SurfaceRequest$2@8099421
05-17 23:56:17.662 19881 19881 D DeferrableSurface: surface closed,  useCount=1 closed=true androidx.camera.core.impl.ImmediateSurface@d5d2ad2
05-17 23:56:17.716 19881 20484 D UseCaseAttachState: Active and attached use case: [] for camera: 0
05-17 23:56:17.717 19881 20484 D DeferrableSurface: use count-1,  useCount=0 closed=true androidx.camera.core.SurfaceRequest$2@8099421
05-17 23:56:17.717 19881 20484 D DeferrableSurface: Surface no longer in use[total_surfaces=2, used_surfaces=1](androidx.camera.core.SurfaceRequest$2@8099421}
05-17 23:56:17.717 19881 20484 D DeferrableSurface: Surface terminated[total_surfaces=1, used_surfaces=1](androidx.camera.core.SurfaceRequest$2@8099421}
05-17 23:56:17.717 19881 20484 D DeferrableSurface: use count-1,  useCount=0 closed=true androidx.camera.core.impl.ImmediateSurface@d5d2ad2
05-17 23:56:17.717 19881 20484 D DeferrableSurface: Surface no longer in use[total_surfaces=1, used_surfaces=0](androidx.camera.core.impl.ImmediateSurface@d5d2ad2}
05-17 23:56:17.717 19881 20484 D DeferrableSurface: Surface terminated[total_surfaces=0, used_surfaces=0](androidx.camera.core.impl.ImmediateSurface@d5d2ad2}
05-17 23:56:17.718 19881 19881 D TextureViewImpl: Safe to release surface.
05-17 23:56:17.718 19881 20484 D Camera2CameraImpl: {Camera@c7baa41[id=0]} CameraDevice.onClosed()
05-17 23:56:17.718 19881 20484 D Camera2CameraImpl: {Camera@c7baa41[id=0]} Transitioning camera internal state: CLOSING --> INITIALIZED
05-17 23:56:17.718 19881 19881 D TextureViewImpl: SurfaceTexture about to manually be destroyed
05-17 23:56:17.718 19881 20484 D CameraStateRegistry: Recalculating open cameras:
05-17 23:56:17.718 19881 20484 D CameraStateRegistry: Camera                                       State
05-17 23:56:17.718 19881 20484 D CameraStateRegistry: -------------------------------------------------------------------
05-17 23:56:17.718 19881 20484 D CameraStateRegistry: Camera@c7baa41[id=0]                         CLOSED
05-17 23:56:17.718 19881 20484 D CameraStateRegistry: Camera@fbfc940[id=1]                         UNKNOWN
05-17 23:56:17.718 19881 20484 D CameraStateRegistry: -------------------------------------------------------------------
05-17 23:56:17.718 19881 20484 D CameraStateRegistry: Open count: 0 (Max allowed: 1)
05-17 23:56:17.718 19881 20484 D CameraStateMachine: New public camera state CameraState{type=CLOSED, error=null} from CLOSED and null
05-17 23:56:17.718 19881 20484 D CameraStateMachine: Publishing new public camera state CameraState{type=CLOSED, error=null}

