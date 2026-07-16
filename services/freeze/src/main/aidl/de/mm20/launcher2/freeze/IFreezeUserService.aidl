package de.mm20.launcher2.freeze;

interface IFreezeUserService {
    void destroy() = 16777114; // Special destroy transaction code required by Shizuku

    boolean runShellCommand(String command) = 1;
}
