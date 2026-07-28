package de.mm20.launcher2.freeze.providers

import android.app.admin.DeviceAdminReceiver

/**
 * Registered as this app's device admin component. By itself, being a plain device admin grants
 * none of the app-suspend/hide capabilities [DeviceOwnerProvider] needs - those require the app to
 * additionally hold *device owner* status, which can only be established via
 * `adb shell dpm set-device-owner <this receiver's component>` on a device with no other accounts
 * or owners (see the guided setup screen). This class has no behavior of its own; it exists only
 * as the component that command points at.
 */
class DeviceOwnerAdminReceiver : DeviceAdminReceiver()
