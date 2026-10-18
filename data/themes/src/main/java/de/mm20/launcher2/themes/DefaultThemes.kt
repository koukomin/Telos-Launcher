package de.mm20.launcher2.themes

import java.util.UUID


val DefaultThemeId = UUID(0L, 0L)

val HighContrastThemeId = UUID(0L, 2L)
val BlackAndWhiteThemeId = UUID(0L, 1L)

val AmoledPresetId = UUID(0L, 100L)
val CyberpunkPresetId = UUID(0L, 101L)
val AmoledRedPresetId = UUID(0L, 102L)
val AmoledGreenPresetId = UUID(0L, 103L)
val AmoledYellowPresetId = UUID(0L, 104L)
val AmoledLightBluePresetId = UUID(0L, 105L)
val AmoledMagentaPresetId = UUID(0L, 106L)
val AmoledOrangePresetId = UUID(0L, 107L)

fun isAmoledFamily(id: UUID): Boolean = id == AmoledPresetId ||
    id == AmoledRedPresetId ||
    id == AmoledGreenPresetId ||
    id == AmoledYellowPresetId ||
    id == AmoledLightBluePresetId ||
    id == AmoledMagentaPresetId ||
    id == AmoledOrangePresetId

val ExtraRoundShapesId = UUID(0L, 1L)
val CutShapesId = UUID(0L, 2L)
val RectShapesId = UUID(0L, 3L)

val SemiTransparentId = UUID(0L, 1L)

val SystemFontId = UUID(0L, 1L)
val MonospaceId = UUID(0L, 2L)
val SerifId = UUID(0L, 3L)
val RoundedTypographyId = UUID(0L, 4L)