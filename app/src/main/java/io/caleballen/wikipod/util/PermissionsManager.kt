package io.caleballen.wikipod.util

import android.app.Activity
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.support.v4.content.PermissionChecker
import android.support.v7.app.AlertDialog
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import timber.log.Timber

/**
 * Created by caleb on 10/25/2017.
 */
fun getPermission(context: Activity,
               permission: String,
               permissionRequest: String,
               deniedResponse: String,
               successCallback: () -> Unit) {
    Timber.v("Getting Permissions: $permission")
    val getPermissions = { dialog: DialogInterface, which: Int ->
        Dexter.withActivity(context)
                .withPermission(permission)
                .withListener(object : PermissionListener {
                    override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest, token: PermissionToken) {
                        token.continuePermissionRequest()
                    }

                    override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                        Timber.v("Permission Granted.")
                        successCallback()
                    }

                    override fun onPermissionDenied(response: PermissionDeniedResponse) {
                        Timber.v("Permission Denied.")
                        AlertDialog.Builder(context)
                                .setMessage(deniedResponse)
                                .setPositiveButton(android.R.string.ok, {_, _ ->  })
                                .show()
                    }
                })
                .check()
    }

    if (PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED) {
        successCallback()
    }else{
        AlertDialog.Builder(context)
                .setMessage(permissionRequest)
                .setPositiveButton(android.R.string.ok, getPermissions)
                .show()
    }
}