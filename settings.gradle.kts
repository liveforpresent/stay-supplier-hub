rootProject.name = "stay-supplier-hub"

include(
    ":app",
    ":catalog:api",
    ":catalog:domain",
    ":catalog:port",
    ":catalog:application",
    ":catalog:adapter:persistence",
    ":search:domain",
    ":search:port",
    ":search:application",
    ":search:adapter:web",
    ":integration:supplier-a",
    ":integration:supplier-b",
    ":shared:infrastructure",
    ":mock-supplier",
)
