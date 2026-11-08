#pragma once
#include <cstdio>
#include <dirent.h>
#include <sys/stat.h>
#include <string>
#include <stdexcept>
#include <android/log.h>

// Root supplied from ANativeActivity::internalDataPath. This directory is private
// to the app; Android removes it with the app during uninstall.
inline std::string gameExecutablePath(const std::string& root) {
    for (const char* name : {"speed.exe", "Speed.exe"}) {
        const std::string path = root + "/" + name;
        FILE* exe = std::fopen(path.c_str(), "rb");
        if (exe) { std::fclose(exe); return path; }
    }
    throw std::runtime_error("Cannot read bundled speed.exe from the app-private game folder");
}

inline std::string checkGameData(const std::string& root) {
    const std::string executable = gameExecutablePath(root);
    FILE* exe = std::fopen(executable.c_str(), "rb");
    if (!exe) throw std::runtime_error("Cannot open Most Wanted executable in app-private storage");
    unsigned char magic[2]{};
    const size_t read = std::fread(magic, 1, 2, exe);
    std::fclose(exe);
    struct stat info{};
    if (read != 2 || magic[0] != 'M' || magic[1] != 'Z' ||
        stat(executable.c_str(), &info) || info.st_size != 6029312)
        throw std::runtime_error("speed.exe does not match the expected Most Wanted image size/header");
    for (const char* name : {"CARS", "FRONTEND", "GLOBAL", "LANGUAGES", "MOVIES", "NIS", "SOUND", "TRACKS"}) {
        const auto path = root + "/" + name;
        DIR* dir = opendir(path.c_str());
        if (!dir) throw std::runtime_error("Cannot read game directory: " + path);
        unsigned files = 0;
        while (auto* entry = readdir(dir)) if (entry->d_name[0] != '.') ++files;
        closedir(dir);
        if (!files) throw std::runtime_error("Empty game directory: " + path);
        __android_log_print(ANDROID_LOG_INFO, "NFSMW", "Data directory %s: %u entries", name, files);
    }
    __android_log_print(ANDROID_LOG_INFO, "NFSMW", "Private game data readable at %s; executable header/size OK", executable.c_str());
    return executable;
}
