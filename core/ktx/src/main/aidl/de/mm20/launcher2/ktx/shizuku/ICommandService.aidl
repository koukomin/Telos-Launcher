package de.mm20.launcher2.ktx.shizuku;
interface ICommandService {
    int getUid() = 1;
    IBinder start(in String[] command, in String[] environment, String directory) = 2;
    void destroy() = 16777114;
}
