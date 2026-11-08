package dev.coobvo.prismmedia

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.findFreeRegister
import dev.coobvo.prismmedia.Constants.COMPATIBILITY_PRISM
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION = "Ldev/coobvo/prismmedia/PrismMediaBridge;"

/** Base conversion used by Prism's queue, search, local files and playback paths. */
private object PrismSongToMediaItemFingerprint : Fingerprint(
    definingClass = "Lcom/prism/music/playback/MediaItemsKt;",
    name = "toMediaItem",
    isStatic = true,
    returnType = "Landroidx/media3/common/MediaItem;",
    parameters = listOf(
        "Lcom/prism/music/data/model/Song;",
        "Ljava/lang/String;"
    ),
    strings = listOf("prism.song")
)

/** MediaTree can replace Song extras with browse-group hints, so decorate its final returned item too. */
private object PrismBrowseMediaItemFingerprint : Fingerprint(
    definingClass = "Lcom/prism/music/playback/MediaTree;",
    name = "playable",
    isStatic = false,
    returnType = "Landroidx/media3/common/MediaItem;",
    parameters = listOf(
        "Lcom/prism/music/data/model/Song;",
        "Landroid/os/Bundle;"
    )
)

/** Prism's player keeps the authoritative active lyrics in this state object. */
private object PrismLyricsSetLyricsFingerprint : Fingerprint(
    definingClass = "Lcom/prism/music/ui/player/LyricsState;",
    name = "setLyrics",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Lcom/prism/music/data/lyrics/Lyrics;")
)

private object PrismLyricsSetSourceFingerprint : Fingerprint(
    definingClass = "Lcom/prism/music/ui/player/LyricsState;",
    name = "setSource",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Lcom/prism/music/data/prefs/LyricsSource;")
)

private object PrismLyricsSetLoadingFingerprint : Fingerprint(
    definingClass = "Lcom/prism/music/ui/player/LyricsState;",
    name = "setLoading",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Z")
)

private object PrismLyricsSetWaitingFingerprint : Fingerprint(
    definingClass = "Lcom/prism/music/ui/player/LyricsState;",
    name = "setWaiting",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Z")
)

private object PrismLyricsSetSettledFingerprint : Fingerprint(
    definingClass = "Lcom/prism/music/ui/player/LyricsState;",
    name = "setSettled",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Z")
)

/** Existing exported provider; only its /bridge query path is intercepted. */
private object PrismArtworkQueryFingerprint : Fingerprint(
    definingClass = "Lcom/prism/music/playback/ArtworkProvider;",
    name = "query",
    isStatic = false,
    returnType = "Landroid/database/Cursor;",
    parameters = listOf(
        "Landroid/net/Uri;",
        "[Ljava/lang/String;",
        "Ljava/lang/String;",
        "[Ljava/lang/String;",
        "Ljava/lang/String;"
    )
)

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.decorateMediaItemReturns() {
    val returnIndexes = instructions.withIndex()
        .filter { it.value.opcode == Opcode.RETURN_OBJECT }
        .map { it.index }
        .sortedDescending()

    if (returnIndexes.isEmpty()) {
        throw PatchException("Prism MediaItem factory has no object return")
    }

    returnIndexes.forEach { index ->
        val register = getInstruction<OneRegisterInstruction>(index).registerA
        addInstructions(
            index,
            """
                invoke-static { v$register }, $EXTENSION->decorateMediaItem(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v$register
                check-cast v$register, Landroidx/media3/common/MediaItem;
            """
        )
    }
}

/** Re-publish after each authoritative state mutation, not on a timer or through a second provider. */
private fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.captureLyricsStateOnReturns() {
    val returnIndexes = instructions.withIndex()
        .filter { it.value.opcode == Opcode.RETURN_VOID }
        .map { it.index }
        .sortedDescending()

    if (returnIndexes.isEmpty()) {
        throw PatchException("Prism LyricsState setter has no void return")
    }

    returnIndexes.forEach { index ->
        addInstructions(
            index,
            "invoke-static { p0 }, $EXTENSION->captureLyricsState(Ljava/lang/Object;)V"
        )
    }
}

@Suppress("unused")
val prismMediaBridgePatch = patch(
    name = "Prism media details for coobvo",
    description = "Exports Prism's local stream and playback diagnostics to coobvo Dynamic Island."
) {
    compatibleWith(COMPATIBILITY_PRISM)
    extendWith("extensions/prism-media-bridge.mpe")

    execute {
        PrismSongToMediaItemFingerprint.method.decorateMediaItemReturns()
        PrismBrowseMediaItemFingerprint.method.decorateMediaItemReturns()

        // Snapshot Prism's own resolved lyric state after it changes. No Compose UI is called from System UI.
        PrismLyricsSetLyricsFingerprint.method.captureLyricsStateOnReturns()
        PrismLyricsSetSourceFingerprint.method.captureLyricsStateOnReturns()
        PrismLyricsSetLoadingFingerprint.method.captureLyricsStateOnReturns()
        PrismLyricsSetWaitingFingerprint.method.captureLyricsStateOnReturns()
        PrismLyricsSetSettledFingerprint.method.captureLyricsStateOnReturns()

        // Preserve ArtworkProvider's existing artwork routes. Only /bridge returns a cursor here.
        PrismArtworkQueryFingerprint.method.apply {
            val free = findFreeRegister(0)
            addInstructionsWithLabels(
                0,
                """
                    invoke-static/range { p0 .. p1 }, $EXTENSION->queryBridge(Landroid/content/ContentProvider;Landroid/net/Uri;)Landroid/database/Cursor;
                    move-result-object v$free
                    if-eqz v$free, :coobvo_prism_bridge_continue
                    return-object v$free
                    :coobvo_prism_bridge_continue
                    nop
                """
            )
        }
    }
}
