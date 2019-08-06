package com.example.gse.imageloader;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class PermissionsUtility
{
    private static Activity activity;
    private static PermissionsUtility _instance;
    public static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    private PermissionsUtility(Activity activity) {
        PermissionsUtility.activity = activity;
    }

    /**
     * @return
     */
    public static PermissionsUtility getInstance(Activity mActivity)
    {
        try
        {
             activity = mActivity;
            if (_instance == null)
            {
                _instance = new PermissionsUtility(activity);
            }
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        return _instance;
    }

    /**
     *
     */
    public static boolean checkPermissions(String[] permissions)
    {
        boolean isPermissionGranted = true;
        try
        {
            List<String> permissionsList = new ArrayList<>();
            List<String> permissionsNeeded = new ArrayList<>();

            for (int i = 0; i < permissions.length; i++)
            {
               if(!addPermission(permissionsList, permissions[i]))
               {
                   permissionsNeeded.add(permissions[i]);
               }
            }

            if (permissionsList.size() > 0)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                {
                    activity.requestPermissions(permissionsList.toArray(new String[permissionsList.size()]), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                }

                isPermissionGranted = false;
            }
            else
            {
                isPermissionGranted = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return isPermissionGranted;
    }

    /**
     *
     */
    public static boolean isPermissionGiven(String[] permissions)
    {
        try
        {
            for (int i = 0; i < permissions.length; i++)
            {
                if (ContextCompat.checkSelfPermission(activity, permissions[i]) != PackageManager.PERMISSION_GRANTED)
                {
                    return false;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    /**
     * @param permissionsList
     * @param permission
     * @return
     */
    private static boolean addPermission(List<String> permissionsList, String permission)
    {
        try
        {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED)
            {
                permissionsList.add(permission);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                {
                    if (!activity.shouldShowRequestPermissionRationale(permission))
                        return false;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return true;
    }
}
