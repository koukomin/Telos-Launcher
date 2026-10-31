package de.mm20.launcher2.appmanagement.stealth;

interface ITelosPrivilegedDaemon {
    int getUid() = 1;
    String exec(String command) = 2;
    boolean grantWriteSecureSettings() = 3;
}
