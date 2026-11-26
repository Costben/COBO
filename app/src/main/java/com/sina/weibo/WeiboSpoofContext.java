package com.sina.weibo;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.util.Base64;

/**
 * 为未打补丁的官方 `libwbutil.so` 提供 `com.sina.weibo` 包名与签名摘要上下文，
 * 让 `newCalculateS` / `getIValue` / `getParam` 与原版客户端算出完全一致的密钥和签名。
 */
public final class WeiboSpoofContext extends ContextWrapper {

    private static final String WEIBO_PKG = "com.sina.weibo";
    private static final String WEIBO_SIG_B64 =
        "MIIClTCCAf6gAwIBAgIES07xvzANBgkqhkiG9w0BAQUFADCBjTELMAkGA1UEBhMCQ04xEDAOBgNV"
        + "BAgTB0JlaUppbmcxEDAOBgNVBAcTB0JlaUppbmcxLDAqBgNVBAoTI1NpbmEuQ29tIFRlY2hub2xv"
        + "Z3kgKENoaW5hKSBDby4gTHRkMSwwKgYDVQQLEyNTaW5hLkNvbSBUZWNobm9sb2d5IChDaGluYSkg"
        + "Q28uIEx0ZDAgFw0xMDAxMTQxMDI4MTVaGA8yMDYwMDEwMjEwMjgxNVowgY0xCzAJBgNVBAYTAkNO"
        + "MRAwDgYDVQQIEwdCZWlKaW5nMRAwDgYDVQQHEwdCZWlKaW5nMSwwKgYDVQQKEyNTaW5hLkNvbSBU"
        + "ZWNobm9sb2d5IChDaGluYSkgQ28uIEx0ZDEsMCoGA1UECxMjU2luYS5Db20gVGVjaG5vbG9neSAo"
        + "Q2hpbmEpIENvLiBMdGQwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAJ02cRW8IGyGwje7Vsjp"
        + "AzERiJtWkfBRso0aqOQrZrdBNldjW0R4bqfoXUUaEqgqMx/O2ZxIcXkiFwt/ybwQQHU8DTi0zysi"
        + "CUsd98VXBbCYlEHnWROhqL0rxZGqcpoQE8J3wByYy+x9pa13eLL61iuFrCnKKM7ViGOMmNa331oT"
        + "AgMBAAEwDQYJKoZIhvcNAQEFBQADgYEACtS0xN7IAL2P0pka39cGdvzoupaSrlBHX2DsRo0bdYpm"
        + "XpYaOu2+zp/U186Slc2D9fGdxEGgZWidmCD67bt8SkxGNfW6EpP22kty7TL7h5X3NqIMlc2ndkAg"
        + "mQVPzO+0oaVYZkq41jcoj+zrqVCKqQf8H+KxrloN7JVO2DHAvqQ=";

    private final PackageInfo spoofInfo;
    private final PackageManager spoofPm;

    public WeiboSpoofContext(Context base) {
        super(base.getApplicationContext() != null ? base.getApplicationContext() : base);
        PackageInfo info = new PackageInfo();
        info.packageName = WEIBO_PKG;
        info.signatures = new Signature[] { new Signature(Base64.getDecoder().decode(WEIBO_SIG_B64)) };
        this.spoofInfo = info;
        this.spoofPm = new DelegatingPackageManager(getBaseContext().getPackageManager(), info);
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public String getPackageName() {
        return WEIBO_PKG;
    }

    @Override
    public PackageManager getPackageManager() {
        return spoofPm;
    }

    private static final class DelegatingPackageManager extends PackageManager {
        private final PackageManager delegate;
        private final PackageInfo spoofInfo;

        DelegatingPackageManager(PackageManager delegate, PackageInfo spoofInfo) {
            this.delegate = delegate;
            this.spoofInfo = spoofInfo;
        }

        @Override public void addPackageToPreferred(java.lang.String a0) { delegate.addPackageToPreferred(a0); }
        @Override public boolean addPermission(android.content.pm.PermissionInfo a0) { return delegate.addPermission(a0); }
        @Override public boolean addPermissionAsync(android.content.pm.PermissionInfo a0) { return delegate.addPermissionAsync(a0); }
        @Override public void addPreferredActivity(android.content.IntentFilter a0, int a1, android.content.ComponentName[] a2, android.content.ComponentName a3) { delegate.addPreferredActivity(a0, a1, a2, a3); }
        @Override public boolean canRequestPackageInstalls() { return delegate.canRequestPackageInstalls(); }
        @Override public java.lang.String[] canonicalToCurrentPackageNames(java.lang.String[] a0) { return delegate.canonicalToCurrentPackageNames(a0); }
        @Override public int checkPermission(java.lang.String a0, java.lang.String a1) { return delegate.checkPermission(a0, a1); }
        @Override public int checkSignatures(int a0, int a1) { return delegate.checkSignatures(a0, a1); }
        @Override public int checkSignatures(java.lang.String a0, java.lang.String a1) { return delegate.checkSignatures(a0, a1); }
        @Override public void clearInstantAppCookie() { delegate.clearInstantAppCookie(); }
        @Override public void clearPackagePreferredActivities(java.lang.String a0) { delegate.clearPackagePreferredActivities(a0); }
        @Override public java.lang.String[] currentToCanonicalPackageNames(java.lang.String[] a0) { return delegate.currentToCanonicalPackageNames(a0); }
        @Override public void extendVerificationTimeout(int a0, int a1, long a2) { delegate.extendVerificationTimeout(a0, a1, a2); }
        @Override public android.graphics.drawable.Drawable getActivityBanner(android.content.ComponentName a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getActivityBanner(a0); }
        @Override public android.graphics.drawable.Drawable getActivityBanner(android.content.Intent a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getActivityBanner(a0); }
        @Override public android.graphics.drawable.Drawable getActivityIcon(android.content.ComponentName a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getActivityIcon(a0); }
        @Override public android.graphics.drawable.Drawable getActivityIcon(android.content.Intent a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getActivityIcon(a0); }
        @Override public android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getActivityInfo(a0, a1); }
        @Override public android.graphics.drawable.Drawable getActivityLogo(android.content.ComponentName a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getActivityLogo(a0); }
        @Override public android.graphics.drawable.Drawable getActivityLogo(android.content.Intent a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getActivityLogo(a0); }
        @Override public java.util.List<android.content.pm.PermissionGroupInfo> getAllPermissionGroups(int a0) { return delegate.getAllPermissionGroups(a0); }
        @Override public android.graphics.drawable.Drawable getApplicationBanner(android.content.pm.ApplicationInfo a0) { return delegate.getApplicationBanner(a0); }
        @Override public android.graphics.drawable.Drawable getApplicationBanner(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getApplicationBanner(a0); }
        @Override public int getApplicationEnabledSetting(java.lang.String a0) { return delegate.getApplicationEnabledSetting(a0); }
        @Override public android.graphics.drawable.Drawable getApplicationIcon(android.content.pm.ApplicationInfo a0) { return delegate.getApplicationIcon(a0); }
        @Override public android.graphics.drawable.Drawable getApplicationIcon(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getApplicationIcon(a0); }
        @Override public android.content.pm.ApplicationInfo getApplicationInfo(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getApplicationInfo(a0, a1); }
        @Override public java.lang.CharSequence getApplicationLabel(android.content.pm.ApplicationInfo a0) { return delegate.getApplicationLabel(a0); }
        @Override public android.graphics.drawable.Drawable getApplicationLogo(android.content.pm.ApplicationInfo a0) { return delegate.getApplicationLogo(a0); }
        @Override public android.graphics.drawable.Drawable getApplicationLogo(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getApplicationLogo(a0); }
        @Override public android.content.pm.ChangedPackages getChangedPackages(int a0) { return delegate.getChangedPackages(a0); }
        @Override public int getComponentEnabledSetting(android.content.ComponentName a0) { return delegate.getComponentEnabledSetting(a0); }
        @Override public android.graphics.drawable.Drawable getDefaultActivityIcon() { return delegate.getDefaultActivityIcon(); }
        @Override public android.graphics.drawable.Drawable getDrawable(java.lang.String a0, int a1, android.content.pm.ApplicationInfo a2) { return delegate.getDrawable(a0, a1, a2); }
        @Override public java.util.List<android.content.pm.ApplicationInfo> getInstalledApplications(int a0) { return delegate.getInstalledApplications(a0); }
        @Override public java.util.List<android.content.pm.PackageInfo> getInstalledPackages(int a0) { return delegate.getInstalledPackages(a0); }
        @Override public java.lang.String getInstallerPackageName(java.lang.String a0) { return delegate.getInstallerPackageName(a0); }
        @Override public byte[] getInstantAppCookie() { return delegate.getInstantAppCookie(); }
        @Override public int getInstantAppCookieMaxBytes() { return delegate.getInstantAppCookieMaxBytes(); }
        @Override public android.content.pm.InstrumentationInfo getInstrumentationInfo(android.content.ComponentName a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getInstrumentationInfo(a0, a1); }
        @Override public android.content.Intent getLaunchIntentForPackage(java.lang.String a0) { return delegate.getLaunchIntentForPackage(a0); }
        @Override public android.content.Intent getLeanbackLaunchIntentForPackage(java.lang.String a0) { return delegate.getLeanbackLaunchIntentForPackage(a0); }
        @Override public java.lang.String getNameForUid(int a0) { return delegate.getNameForUid(a0); }
        @Override public int[] getPackageGids(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getPackageGids(a0); }
        @Override public int[] getPackageGids(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getPackageGids(a0, a1); }
        @Override public android.content.pm.PackageInfo getPackageInfo(android.content.pm.VersionedPackage a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getPackageInfo(a0, a1); }
        @Override public android.content.pm.PackageInfo getPackageInfo(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { if (WEIBO_PKG.equals(a0)) { return spoofInfo; } return delegate.getPackageInfo(a0, a1); }
        @Override public android.content.pm.PackageInstaller getPackageInstaller() { return delegate.getPackageInstaller(); }
        @Override public int getPackageUid(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getPackageUid(a0, a1); }
        @Override public java.lang.String[] getPackagesForUid(int a0) { return delegate.getPackagesForUid(a0); }
        @Override public java.util.List<android.content.pm.PackageInfo> getPackagesHoldingPermissions(java.lang.String[] a0, int a1) { return delegate.getPackagesHoldingPermissions(a0, a1); }
        @Override public android.content.pm.PermissionGroupInfo getPermissionGroupInfo(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getPermissionGroupInfo(a0, a1); }
        @Override public android.content.pm.PermissionInfo getPermissionInfo(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getPermissionInfo(a0, a1); }
        @Override public int getPreferredActivities(java.util.List<android.content.IntentFilter> a0, java.util.List<android.content.ComponentName> a1, java.lang.String a2) { return delegate.getPreferredActivities(a0, a1, a2); }
        @Override public java.util.List<android.content.pm.PackageInfo> getPreferredPackages(int a0) { return delegate.getPreferredPackages(a0); }
        @Override public android.content.pm.ProviderInfo getProviderInfo(android.content.ComponentName a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getProviderInfo(a0, a1); }
        @Override public android.content.pm.ActivityInfo getReceiverInfo(android.content.ComponentName a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getReceiverInfo(a0, a1); }
        @Override public android.content.res.Resources getResourcesForActivity(android.content.ComponentName a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getResourcesForActivity(a0); }
        @Override public android.content.res.Resources getResourcesForApplication(android.content.pm.ApplicationInfo a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getResourcesForApplication(a0); }
        @Override public android.content.res.Resources getResourcesForApplication(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getResourcesForApplication(a0); }
        @Override public android.content.pm.ServiceInfo getServiceInfo(android.content.ComponentName a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.getServiceInfo(a0, a1); }
        @Override public java.util.List<android.content.pm.SharedLibraryInfo> getSharedLibraries(int a0) { return delegate.getSharedLibraries(a0); }
        @Override public android.content.pm.FeatureInfo[] getSystemAvailableFeatures() { return delegate.getSystemAvailableFeatures(); }
        @Override public java.lang.String[] getSystemSharedLibraryNames() { return delegate.getSystemSharedLibraryNames(); }
        @Override public java.lang.CharSequence getText(java.lang.String a0, int a1, android.content.pm.ApplicationInfo a2) { return delegate.getText(a0, a1, a2); }
        @Override public android.graphics.drawable.Drawable getUserBadgedDrawableForDensity(android.graphics.drawable.Drawable a0, android.os.UserHandle a1, android.graphics.Rect a2, int a3) { return delegate.getUserBadgedDrawableForDensity(a0, a1, a2, a3); }
        @Override public android.graphics.drawable.Drawable getUserBadgedIcon(android.graphics.drawable.Drawable a0, android.os.UserHandle a1) { return delegate.getUserBadgedIcon(a0, a1); }
        @Override public java.lang.CharSequence getUserBadgedLabel(java.lang.CharSequence a0, android.os.UserHandle a1) { return delegate.getUserBadgedLabel(a0, a1); }
        @Override public android.content.res.XmlResourceParser getXml(java.lang.String a0, int a1, android.content.pm.ApplicationInfo a2) { return delegate.getXml(a0, a1, a2); }
        @Override public boolean hasSystemFeature(java.lang.String a0) { return delegate.hasSystemFeature(a0); }
        @Override public boolean hasSystemFeature(java.lang.String a0, int a1) { return delegate.hasSystemFeature(a0, a1); }
        @Override public boolean isInstantApp() { return delegate.isInstantApp(); }
        @Override public boolean isInstantApp(java.lang.String a0) { return delegate.isInstantApp(a0); }
        @Override public boolean isPermissionRevokedByPolicy(java.lang.String a0, java.lang.String a1) { return delegate.isPermissionRevokedByPolicy(a0, a1); }
        @Override public boolean isSafeMode() { return delegate.isSafeMode(); }
        @Override public java.util.List<android.content.pm.ResolveInfo> queryBroadcastReceivers(android.content.Intent a0, int a1) { return delegate.queryBroadcastReceivers(a0, a1); }
        @Override public java.util.List<android.content.pm.ProviderInfo> queryContentProviders(java.lang.String a0, int a1, int a2) { return delegate.queryContentProviders(a0, a1, a2); }
        @Override public java.util.List<android.content.pm.InstrumentationInfo> queryInstrumentation(java.lang.String a0, int a1) { return delegate.queryInstrumentation(a0, a1); }
        @Override public java.util.List<android.content.pm.ResolveInfo> queryIntentActivities(android.content.Intent a0, int a1) { return delegate.queryIntentActivities(a0, a1); }
        @Override public java.util.List<android.content.pm.ResolveInfo> queryIntentActivityOptions(android.content.ComponentName a0, android.content.Intent[] a1, android.content.Intent a2, int a3) { return delegate.queryIntentActivityOptions(a0, a1, a2, a3); }
        @Override public java.util.List<android.content.pm.ResolveInfo> queryIntentContentProviders(android.content.Intent a0, int a1) { return delegate.queryIntentContentProviders(a0, a1); }
        @Override public java.util.List<android.content.pm.ResolveInfo> queryIntentServices(android.content.Intent a0, int a1) { return delegate.queryIntentServices(a0, a1); }
        @Override public java.util.List<android.content.pm.PermissionInfo> queryPermissionsByGroup(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException { return delegate.queryPermissionsByGroup(a0, a1); }
        @Override public void removePackageFromPreferred(java.lang.String a0) { delegate.removePackageFromPreferred(a0); }
        @Override public void removePermission(java.lang.String a0) { delegate.removePermission(a0); }
        @Override public android.content.pm.ResolveInfo resolveActivity(android.content.Intent a0, int a1) { return delegate.resolveActivity(a0, a1); }
        @Override public android.content.pm.ProviderInfo resolveContentProvider(java.lang.String a0, int a1) { return delegate.resolveContentProvider(a0, a1); }
        @Override public android.content.pm.ResolveInfo resolveService(android.content.Intent a0, int a1) { return delegate.resolveService(a0, a1); }
        @Override public void setApplicationCategoryHint(java.lang.String a0, int a1) { delegate.setApplicationCategoryHint(a0, a1); }
        @Override public void setApplicationEnabledSetting(java.lang.String a0, int a1, int a2) { delegate.setApplicationEnabledSetting(a0, a1, a2); }
        @Override public void setComponentEnabledSetting(android.content.ComponentName a0, int a1, int a2) { delegate.setComponentEnabledSetting(a0, a1, a2); }
        @Override public void setInstallerPackageName(java.lang.String a0, java.lang.String a1) { delegate.setInstallerPackageName(a0, a1); }
        @Override public void updateInstantAppCookie(byte[] a0) { delegate.updateInstantAppCookie(a0); }
        @Override public void verifyPendingInstall(int a0, int a1) { delegate.verifyPendingInstall(a0, a1); }
    }
}
