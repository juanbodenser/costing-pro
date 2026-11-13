package com.jboden.costingpro.export

/** What importing a backup does with the data already in the app. */
enum class ImportMode {
    /** Everything is deleted and replaced by the backup. */
    REPLACE,

    /** The backup is added to the current data, which stays as it is. */
    ADD,
}
