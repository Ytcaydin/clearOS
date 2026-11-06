package com.clearos.ultra;
interface IUltraService {
    // AIDL: either every method has an explicit transaction id or none does.
    String trimCaches() = 1;
    // Reserved by Shizuku for UserService teardown.
    void destroy() = 16777114;
}
