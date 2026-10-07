package org.rsmod.content.areas.misc.isleofsouls.teams

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** File helpers shared by everything that saves event data under `.data/league`. */
internal object LeagueFiles {
    /** Relative to the server's working directory, like `.data/server.toml`. */
    val DATA_DIR: Path = Paths.get(".data", "league")

    /**
     * Writes [text] to a temporary file first and moves it into place, so a crash part-way through
     * cannot leave half a file behind.
     */
    fun writeAtomically(file: Path, text: String) {
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temp, text)
        try {
            Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, file, REPLACE_EXISTING)
        }
    }
}
