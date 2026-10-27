package de.mm20.launcher2.ktx.shizuku;
interface ICommandService {
    int getUid();
    IBinder start(in String[] command, in String[] environment, String directory);
    void destroy() = 16777114;
}
