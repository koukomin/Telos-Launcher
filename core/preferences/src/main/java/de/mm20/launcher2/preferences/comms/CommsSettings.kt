// === TELOS_PENDING_REVIEW_START: comms_settings_engine ===
package de.mm20.launcher2.preferences.comms

import de.mm20.launcher2.preferences.CommsGroup
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class CommsSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val speedDials
        get() = dataStore.data.map { it.comms.speedDials }

    fun setSpeedDial(digit: Int, number: String?) {
        dataStore.update { data ->
            val currentMap = data.comms.speedDials.toMutableMap()
            if (number.isNullOrEmpty()) {
                currentMap.remove(digit)
            } else {
                currentMap[digit] = number
            }
            data.copy(comms = data.comms.copy(speedDials = currentMap))
        }
    }

    val t9Alphabet
        get() = dataStore.data.map { it.comms.t9Alphabet }

    fun setT9Alphabet(alphabet: String) {
        dataStore.update { it.copy(comms = it.comms.copy(t9Alphabet = alphabet)) }
    }

    val defaultSim
        get() = dataStore.data.map { it.comms.defaultSim }

    fun setDefaultSim(sim: String) {
        dataStore.update { it.copy(comms = it.comms.copy(defaultSim = sim)) }
    }

    val dialpadSounds
        get() = dataStore.data.map { it.comms.dialpadSounds }
    fun setDialpadSounds(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(dialpadSounds = enabled)) }
    }

    val dialpadVibration
        get() = dataStore.data.map { it.comms.dialpadVibration }
    fun setDialpadVibration(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(dialpadVibration = enabled)) }
    }

    val vibrateOnAnswer
        get() = dataStore.data.map { it.comms.vibrateOnAnswer }
    fun setVibrateOnAnswer(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(vibrateOnAnswer = enabled)) }
    }

    val vibrateOnHangup
        get() = dataStore.data.map { it.comms.vibrateOnHangup }
    fun setVibrateOnHangup(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(vibrateOnHangup = enabled)) }
    }

    val clirPrefix
        get() = dataStore.data.map { it.comms.clirPrefix }
    fun setClirPrefix(prefix: String) {
        dataStore.update { it.copy(comms = it.comms.copy(clirPrefix = prefix)) }
    }

    val systemSpamBlocking get() = dataStore.data.map { it.comms.systemSpamBlocking }
    fun setSystemSpamBlocking(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(systemSpamBlocking = enabled)) }
    }
    val onlineCallerLookup get() = dataStore.data.map { it.comms.onlineCallerLookup }
    fun setOnlineCallerLookup(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(onlineCallerLookup = enabled)) }
    }

    val enableSpamBlocking
        get() = dataStore.data.map { it.comms.enableSpamBlocking }
    fun setEnableSpamBlocking(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(enableSpamBlocking = enabled)) }
    }

    val showRecentsFavorites
        get() = dataStore.data.map { it.comms.showRecentsFavorites }
    fun setShowRecentsFavorites(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(showRecentsFavorites = enabled)) }
    }

    val hideDialpadLetters
        get() = dataStore.data.map { it.comms.hideDialpadLetters }
    fun setHideDialpadLetters(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(hideDialpadLetters = enabled)) }
    }

    val allowDialerLandscape
        get() = dataStore.data.map { it.comms.allowDialerLandscape }
    fun setAllowDialerLandscape(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(allowDialerLandscape = enabled)) }
    }

    val callerNotes
        get() = dataStore.data.map { it.comms.callerNotes }
    fun setCallerNote(number: String, note: String?) {
        dataStore.update { data ->
            val map = data.comms.callerNotes.toMutableMap()
            if (note.isNullOrBlank()) map.remove(number) else map[number] = note
            data.copy(comms = data.comms.copy(callerNotes = map))
        }
    }

    val numberDefaultSim
        get() = dataStore.data.map { it.comms.numberDefaultSim }
    fun setNumberDefaultSim(number: String, sim: String?) {
        dataStore.update { data ->
            val map = data.comms.numberDefaultSim.toMutableMap()
            if (sim.isNullOrBlank() || sim == "ask") map.remove(number) else map[number] = sim
            data.copy(comms = data.comms.copy(numberDefaultSim = map))
        }
    }

    val blockHiddenNumbers
        get() = dataStore.data.map { it.comms.blockHiddenNumbers }
    fun setBlockHiddenNumbers(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(blockHiddenNumbers = enabled)) }
    }

    val blockUnknownNumbers
        get() = dataStore.data.map { it.comms.blockUnknownNumbers }
    fun setBlockUnknownNumbers(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(blockUnknownNumbers = enabled)) }
    }

    val blockInternational
        get() = dataStore.data.map { it.comms.blockInternational }
    fun setBlockInternational(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(blockInternational = enabled)) }
    }

    val blockOutgoingCalls
        get() = dataStore.data.map { it.comms.blockOutgoingCalls }
    fun setBlockOutgoingCalls(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(blockOutgoingCalls = enabled)) }
    }

    val homeCountry
        get() = dataStore.data.map { it.comms.homeCountry }
    fun setHomeCountry(region: String) {
        dataStore.update { it.copy(comms = it.comms.copy(homeCountry = region.uppercase())) }
    }

    val clirEnabled
        get() = dataStore.data.map { it.comms.clirEnabled }
    fun setClirEnabled(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(clirEnabled = enabled)) }
    }

    val autoRecordCalls
        get() = dataStore.data.map { it.comms.autoRecordCalls }
    fun setAutoRecordCalls(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(autoRecordCalls = enabled)) }
    }

    val recordingQuality
        get() = dataStore.data.map { it.comms.recordingQuality }
    fun setRecordingQuality(quality: String) {
        dataStore.update { it.copy(comms = it.comms.copy(recordingQuality = quality)) }
    }

    val recordingBackend
        get() = dataStore.data.map { it.comms.recordingBackend }
    fun setRecordingBackend(backend: String) {
        dataStore.update { it.copy(comms = it.comms.copy(recordingBackend = backend)) }
    }

    val rememberDialpad
        get() = dataStore.data.map { it.comms.rememberDialpad }
    fun setRememberDialpad(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(rememberDialpad = enabled)) }
    }

    val lastDialpadDigits
        get() = dataStore.data.map { it.comms.lastDialpadDigits }
    fun setLastDialpadDigits(digits: String) {
        dataStore.update { it.copy(comms = it.comms.copy(lastDialpadDigits = digits.take(32))) }
    }

    val confirmBeforeCall
        get() = dataStore.data.map { it.comms.confirmBeforeCall }
    fun setConfirmBeforeCall(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(confirmBeforeCall = enabled)) }
    }

    val tapToCall
        get() = dataStore.data.map { it.comms.tapToCall }
    fun setTapToCall(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(tapToCall = enabled)) }
    }

    val autoRedial
        get() = dataStore.data.map { it.comms.autoRedial }
    fun setAutoRedial(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(autoRedial = enabled)) }
    }

    val autoRedialAttempts
        get() = dataStore.data.map { it.comms.autoRedialAttempts }
    fun setAutoRedialAttempts(count: Int) {
        dataStore.update { it.copy(comms = it.comms.copy(autoRedialAttempts = count.coerceIn(1, 10))) }
    }

    val autoRedialDelaySec
        get() = dataStore.data.map { it.comms.autoRedialDelaySec }
    fun setAutoRedialDelaySec(sec: Int) {
        dataStore.update { it.copy(comms = it.comms.copy(autoRedialDelaySec = sec.coerceIn(3, 60))) }
    }

    val autoOpenDialpad
        get() = dataStore.data.map { it.comms.autoOpenDialpad }
    fun setAutoOpenDialpad(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(autoOpenDialpad = enabled)) }
    }

    val rejectSmsTemplate
        get() = dataStore.data.map { it.comms.rejectSmsTemplate }
    fun setRejectSmsTemplate(text: String) {
        dataStore.update { it.copy(comms = it.comms.copy(rejectSmsTemplate = text.take(160))) }
    }

    val lastUsedSim
        get() = dataStore.data.map { it.comms.lastUsedSim }
    fun setLastUsedSim(id: String) {
        dataStore.update { it.copy(comms = it.comms.copy(lastUsedSim = id)) }
    }

    val pocketMode
        get() = dataStore.data.map { it.comms.pocketMode }
    fun setPocketMode(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(pocketMode = enabled)) }
    }

    val proximitySpeaker
        get() = dataStore.data.map { it.comms.proximitySpeaker }
    fun setProximitySpeaker(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(proximitySpeaker = enabled)) }
    }

    val showNumbersInRecents
        get() = dataStore.data.map { it.comms.showNumbersInRecents }
    fun setShowNumbersInRecents(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(showNumbersInRecents = enabled)) }
    }

    val missedCallPopup
        get() = dataStore.data.map { it.comms.missedCallPopup }
    fun setMissedCallPopup(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(missedCallPopup = enabled)) }
    }

    val postCallPopup
        get() = dataStore.data.map { it.comms.postCallPopup }
    fun setPostCallPopup(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(postCallPopup = enabled)) }
    }

    val inCallNotes
        get() = dataStore.data.map { it.comms.inCallNotes }
    fun setInCallNotes(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(inCallNotes = enabled)) }
    }

    val contactDefaultNumbers
        get() = dataStore.data.map { it.comms.contactDefaultNumbers }
    fun setContactDefaultNumber(contactId: String, number: String?) {
        dataStore.update { data ->
            val map = data.comms.contactDefaultNumbers.toMutableMap()
            if (number.isNullOrBlank()) map.remove(contactId) else map[contactId] = number
            data.copy(comms = data.comms.copy(contactDefaultNumbers = map))
        }
    }

    val contactDefaultCallApp
        get() = dataStore.data.map { it.comms.contactDefaultCallApp }
    fun setContactDefaultCallApp(number: String, appId: String?) {
        dataStore.update { data ->
            val map = data.comms.contactDefaultCallApp.toMutableMap()
            val key = number.filter { it.isDigit() }
            if (appId.isNullOrBlank() || appId == "phone" || key.isBlank()) map.remove(key) else map[key] = appId
            data.copy(comms = data.comms.copy(contactDefaultCallApp = map))
        }
    }

    val contactDefaultMessageApp
        get() = dataStore.data.map { it.comms.contactDefaultMessageApp }
    fun setContactDefaultMessageApp(number: String, appId: String?) {
        dataStore.update { data ->
            val map = data.comms.contactDefaultMessageApp.toMutableMap()
            val key = number.filter { it.isDigit() }
            if (appId.isNullOrBlank() || appId == "sms" || key.isBlank()) map.remove(key) else map[key] = appId
            data.copy(comms = data.comms.copy(contactDefaultMessageApp = map))
        }
    }

    val archivedConversations
        get() = dataStore.data.map { it.comms.archivedConversations }
    fun setConversationArchived(address: String, archived: Boolean) {
        dataStore.update { data ->
            val map = data.comms.archivedConversations.toMutableMap()
            val key = address.filter { it.isDigit() || it == ',' }
            if (!archived || key.isBlank()) map.remove(key) else map[key] = "1"
            data.copy(comms = data.comms.copy(archivedConversations = map))
        }
    }

    val pinnedConversations
        get() = dataStore.data.map { it.comms.pinnedConversations }
    fun setConversationPinned(address: String, pinned: Boolean) {
        dataStore.update { data ->
            val map = data.comms.pinnedConversations.toMutableMap()
            val key = address.filter { it.isDigit() || it == ',' }
            if (!pinned || key.isBlank()) map.remove(key) else map[key] = "1"
            data.copy(comms = data.comms.copy(pinnedConversations = map))
        }
    }

    val conversationNames
        get() = dataStore.data.map { it.comms.conversationNames }
    fun setConversationName(address: String, name: String?) {
        dataStore.update { data ->
            val map = data.comms.conversationNames.toMutableMap()
            val key = address.filter { it.isDigit() || it == ',' }
            if (name.isNullOrBlank() || key.isBlank()) map.remove(key) else map[key] = name
            data.copy(comms = data.comms.copy(conversationNames = map))
        }
    }

    val smsSignature
        get() = dataStore.data.map { it.comms.smsSignature }
    fun setSmsSignature(value: String) {
        dataStore.update { it.copy(comms = it.comms.copy(smsSignature = value)) }
    }

    val unreadOnTop
        get() = dataStore.data.map { it.comms.unreadOnTop }
    fun setUnreadOnTop(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(unreadOnTop = enabled)) }
    }

    val smsAutoDeleteDays
        get() = dataStore.data.map { it.comms.smsAutoDeleteDays }
    fun setSmsAutoDeleteDays(days: Int) {
        dataStore.update { it.copy(comms = it.comms.copy(smsAutoDeleteDays = days.coerceAtLeast(0))) }
    }

    val blockAlphanumeric
        get() = dataStore.data.map { it.comms.blockAlphanumeric }
    fun setBlockAlphanumeric(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(blockAlphanumeric = enabled)) }
    }

    val smsPopupReply
        get() = dataStore.data.map { it.comms.smsPopupReply }
    fun setSmsPopupReply(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(smsPopupReply = enabled)) }
    }

    val smsE2eEnabled
        get() = dataStore.data.map { it.comms.smsE2eEnabled }
    fun setSmsE2eEnabled(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(smsE2eEnabled = enabled)) }
    }

    val smsBlockRegex
        get() = dataStore.data.map { it.comms.smsBlockRegex }
    fun setSmsBlockRegex(value: String) {
        dataStore.update { it.copy(comms = it.comms.copy(smsBlockRegex = value)) }
    }

    val hiddenNumbers
        get() = dataStore.data.map { it.comms.hiddenNumbers }
    fun setHiddenNumber(number: String, hidden: Boolean) = setHiddenNumbers(listOf(number), hidden)

    /** A contact is one privacy action: persist every number atomically, including legacy aliases. */
    fun setHiddenNumbers(numbers: List<String>, hidden: Boolean) {
        val keys = numbers.map(de.mm20.launcher2.ktx.PhoneNumberKey::of).filter(String::isNotEmpty).toSet()
        if (keys.isEmpty()) return
        dataStore.update { data ->
            val map = data.comms.hiddenNumbers.filterKeys {
                de.mm20.launcher2.ktx.PhoneNumberKey.of(it) !in keys
            }.toMutableMap()
            if (hidden) keys.forEach { map[it] = "1" }
            data.copy(comms = data.comms.copy(hiddenNumbers = map))
        }
    }

    val hideFromContacts
        get() = dataStore.data.map { it.comms.hideFromContacts }
    fun setHideFromContacts(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(hideFromContacts = enabled)) }
    }

    val hideFromRecents
        get() = dataStore.data.map { it.comms.hideFromRecents }
    fun setHideFromRecents(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(hideFromRecents = enabled)) }
    }

    val maskHiddenIncoming
        get() = dataStore.data.map { it.comms.maskHiddenIncoming }
    fun setMaskHiddenIncoming(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(maskHiddenIncoming = enabled)) }
    }

    val stealthHiderMenu
        get() = dataStore.data.map { it.comms.stealthHiderMenu }
    fun setStealthHiderMenu(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(stealthHiderMenu = enabled)) }
    }

    val hiddenNotifDisguised get() = dataStore.data.map { it.comms.hiddenNotifDisguised }
    fun setHiddenNotifDisguised(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(hiddenNotifDisguised = enabled)) }
    }

    val hiddenNotifTitle get() = dataStore.data.map { it.comms.hiddenNotifTitle }
    fun setHiddenNotifTitle(value: String) {
        dataStore.update { it.copy(comms = it.comms.copy(hiddenNotifTitle = value)) }
    }

    val hiddenNotifText get() = dataStore.data.map { it.comms.hiddenNotifText }
    fun setHiddenNotifText(value: String) {
        dataStore.update { it.copy(comms = it.comms.copy(hiddenNotifText = value)) }
    }

    val hiddenMuteRingtone get() = dataStore.data.map { it.comms.hiddenMuteRingtone }
    fun setHiddenMuteRingtone(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(hiddenMuteRingtone = enabled)) }
    }

    val hiddenHideCallScreen get() = dataStore.data.map { it.comms.hiddenHideCallScreen }
    fun setHiddenHideCallScreen(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(hiddenHideCallScreen = enabled)) }
    }

    val dndFavoritesOnlyCall get() = dataStore.data.map { it.comms.dndFavoritesOnlyCall }
    fun setDndFavoritesOnlyCall(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(dndFavoritesOnlyCall = enabled)) }
    }

    val phoneAppLock
        get() = dataStore.data.map { it.comms.phoneAppLock }
    fun setPhoneAppLock(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(phoneAppLock = enabled)) }
    }
    val lockCallRecordings
        get() = dataStore.data.map { it.comms.lockCallRecordings }
    fun setLockCallRecordings(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(lockCallRecordings = enabled)) }
    }

    val hideCallRecordingsFromMedia
        get() = dataStore.data.map { it.comms.hideCallRecordingsFromMedia }
    fun setHideCallRecordingsFromMedia(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(hideCallRecordingsFromMedia = enabled)) }
    }

    val callProtectMode
        get() = dataStore.data.map { it.comms.callProtectMode }
    fun setCallProtectMode(mode: String) {
        dataStore.update { it.copy(comms = it.comms.copy(callProtectMode = mode)) }
    }

    val protectedCallNumbers
        get() = dataStore.data.map { it.comms.protectedCallNumbers }
    fun setProtectedCallNumber(number: String, protected: Boolean) {
        dataStore.update { data ->
            val map = data.comms.protectedCallNumbers.toMutableMap()
            val key = number.filter { it.isDigit() || it == '+' }
            if (!protected || key.isBlank()) map.remove(key) else map[key] = "1"
            data.copy(comms = data.comms.copy(protectedCallNumbers = map))
        }
    }

    val raiseToAnswer
        get() = dataStore.data.map { it.comms.raiseToAnswer }
    fun setRaiseToAnswer(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(raiseToAnswer = enabled)) }
    }
    val flipToDecline
        get() = dataStore.data.map { it.comms.flipToDecline }
    fun setFlipToDecline(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(flipToDecline = enabled)) }
    }
    val rainMode
        get() = dataStore.data.map { it.comms.rainMode }
    fun setRainMode(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(rainMode = enabled)) }
    }
    val volumeDnd
        get() = dataStore.data.map { it.comms.volumeDnd }
    fun setVolumeDnd(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(volumeDnd = enabled)) }
    }
    val volumeDndLockOnly
        get() = dataStore.data.map { it.comms.volumeDndLockOnly }
    fun setVolumeDndLockOnly(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(volumeDndLockOnly = enabled)) }
    }
    val preferredNetworkMode
        get() = dataStore.data.map { it.comms.preferredNetworkMode }
    fun setPreferredNetworkMode(mode: String) {
        dataStore.update { it.copy(comms = it.comms.copy(preferredNetworkMode = mode)) }
    }
    val networkBackend
        get() = dataStore.data.map { it.comms.networkBackend }
    fun setNetworkBackend(backend: String) {
        dataStore.update { it.copy(comms = it.comms.copy(networkBackend = backend)) }
    }
    val screenOffLte
        get() = dataStore.data.map { it.comms.screenOffLte }
    fun setScreenOffLte(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(screenOffLte = enabled)) }
    }
    val batterySaverLte
        get() = dataStore.data.map { it.comms.batterySaverLte }
    fun setBatterySaverLte(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(batterySaverLte = enabled)) }
    }
    val hwuiRenderer
        get() = dataStore.data.map { it.comms.hwuiRenderer }
    fun setHwuiRenderer(mode: String) {
        dataStore.update { it.copy(comms = it.comms.copy(hwuiRenderer = mode)) }
    }
    val hwuiRestoreOnBoot
        get() = dataStore.data.map { it.comms.hwuiRestoreOnBoot }
    fun setHwuiRestoreOnBoot(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(hwuiRestoreOnBoot = enabled)) }
    }
    val hwuiShowRestartNotice
        get() = dataStore.data.map { it.comms.hwuiShowRestartNotice }
    fun setHwuiShowRestartNotice(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(hwuiShowRestartNotice = enabled)) }
    }
    val hwuiWidgetSwitch
        get() = dataStore.data.map { it.comms.hwuiWidgetSwitch }
    fun setHwuiWidgetSwitch(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(hwuiWidgetSwitch = enabled)) }
    }

    /** Keys of the Telos virtual apps that are hidden from the app grid and from search */
    val disabledVirtualApps
        get() = dataStore.data.map { data ->
            data.comms.disabledVirtualApps.split(',').filter { it.isNotBlank() }.toSet()
        }

    /** Telos Media: the space that was open last (music, radio or video) */
    val mediaHubSpace
        get() = dataStore.data.map { it.comms.mediaHubSpace }

    /** Telos Media: ids of the spaces in the order chosen by the user (may be incomplete or empty) */
    val mediaHubOrder
        get() = dataStore.data.map { data -> data.comms.mediaHubOrder.split(',').filter { it.isNotBlank() } }

    fun setMediaHubOrder(ids: List<String>) {
        dataStore.update { it.copy(comms = it.comms.copy(mediaHubOrder = ids.joinToString(","))) }
    }

    fun setMediaHubSpace(space: String) {
        dataStore.update { it.copy(comms = it.comms.copy(mediaHubSpace = space)) }
    }

    val mediaHubCompactList
        get() = dataStore.data.map { it.comms.mediaHubCompactList }

    fun setMediaHubCompactList(compact: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(mediaHubCompactList = compact)) }
    }

    fun setVirtualAppEnabled(key: String, enabled: Boolean) {
        dataStore.update { data ->
            val current = data.comms.disabledVirtualApps.split(',').filter { it.isNotBlank() }.toMutableSet()
            if (enabled) current.remove(key) else current.add(key)
            data.copy(comms = data.comms.copy(disabledVirtualApps = current.joinToString(",")))
        }
    }

    fun setVideoServices(
        tmdbKeyEnc: String,
        subtitleKeyEnc: String,
        subtitleUser: String,
        subtitlePasswordEnc: String,
        languages: String,
        autoDownload: Boolean,
        torrentWifiOnly: Boolean,
    ) {
        dataStore.update {
            it.copy(
                comms = it.comms.copy(
                    tmdbApiKeyEnc = tmdbKeyEnc,
                    subtitleApiKeyEnc = subtitleKeyEnc,
                    subtitleUser = subtitleUser.trim(),
                    subtitlePasswordEnc = subtitlePasswordEnc,
                    subtitleLanguages = languages.replace(" ", ""),
                    subtitleAutoDownload = autoDownload,
                    torrentWifiOnly = torrentWifiOnly,
                )
            )
        }
    }

    val sipEnabled
        get() = dataStore.data.map { it.comms.sipEnabled }
    val sipOutgoing
        get() = dataStore.data.map { it.comms.sipOutgoing }
    fun setSipEnabled(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(sipEnabled = enabled)) }
    }
    fun setSipOutgoing(mode: String) {
        dataStore.update { it.copy(comms = it.comms.copy(sipOutgoing = mode)) }
    }
    fun setSipAccount(user: String, domain: String, displayName: String, passwordEnc: String, verifyServer: Boolean) {
        dataStore.update {
            it.copy(
                comms = it.comms.copy(
                    sipUser = user.trim(),
                    sipDomain = domain.trim(),
                    sipDisplayName = displayName.trim(),
                    sipVerifyServer = verifyServer,
                    sipPasswordEnc = passwordEnc,
                )
            )
        }
    }

    val remotePhonebookEnabled
        get() = dataStore.data.map { it.comms.remotePhonebookEnabled }
    val remotePhonebookHost
        get() = dataStore.data.map { it.comms.remotePhonebookHost }
    val remotePhonebookUser
        get() = dataStore.data.map { it.comms.remotePhonebookUser }
    val remotePhonebookPasswordEnc
        get() = dataStore.data.map { it.comms.remotePhonebookPasswordEnc }
    fun setRemotePhonebook(enabled: Boolean, host: String, user: String, passwordEnc: String) {
        dataStore.update {
            it.copy(
                comms = it.comms.copy(
                    remotePhonebookEnabled = enabled,
                    remotePhonebookHost = host.trim(),
                    remotePhonebookUser = user.trim(),
                    remotePhonebookPasswordEnc = passwordEnc,
                )
            )
        }
    }

    val sim1Color
        get() = dataStore.data.map { it.comms.sim1Color }
    fun setSim1Color(color: String) {
        dataStore.update { it.copy(comms = it.comms.copy(sim1Color = color)) }
    }
    val sim2Color
        get() = dataStore.data.map { it.comms.sim2Color }
    fun setSim2Color(color: String) {
        dataStore.update { it.copy(comms = it.comms.copy(sim2Color = color)) }
    }

    val simNames
        get() = dataStore.data.map { it.comms.simNames }
    fun setSimName(subscriptionId: String, name: String?) {
        dataStore.update {
            val map = it.comms.simNames.toMutableMap()
            if (name.isNullOrBlank()) map.remove(subscriptionId) else map[subscriptionId] = name.trim()
            it.copy(comms = it.comms.copy(simNames = map))
        }
    }

    val answerStyle
        get() = dataStore.data.map { it.comms.answerStyle }
    fun setAnswerStyle(style: String) {
        dataStore.update { it.copy(comms = it.comms.copy(answerStyle = style)) }
    }

    val fullScreenCallerPhoto
        get() = dataStore.data.map { it.comms.fullScreenCallerPhoto }
    fun setFullScreenCallerPhoto(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(fullScreenCallerPhoto = enabled)) }
    }

    val secureCallScreen
        get() = dataStore.data.map { it.comms.secureCallScreen }
    fun setSecureCallScreen(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(secureCallScreen = enabled)) }
    }

    val recordingAutoDeleteDays
        get() = dataStore.data.map { it.comms.recordingAutoDeleteDays }
    fun setRecordingAutoDeleteDays(days: Int) {
        dataStore.update { it.copy(comms = it.comms.copy(recordingAutoDeleteDays = days)) }
    }

        val autoRecordMode
        get() = dataStore.data.map { it.comms.autoRecordMode }
    fun setAutoRecordMode(mode: String) {
        dataStore.update { it.copy(comms = it.comms.copy(autoRecordMode = mode)) }
    }

    val recordProhibitedNumbers
        get() = dataStore.data.map { it.comms.recordProhibitedNumbers }
    fun setNumberRecordProhibited(number: String, prohibited: Boolean) {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isBlank()) return
        dataStore.update {
            val set = it.comms.recordProhibitedNumbers.toMutableSet()
            if (prohibited) set.add(clean) else set.remove(clean)
            val mandatorySet = it.comms.recordMandatoryNumbers.toMutableSet()
            if (prohibited) mandatorySet.remove(clean)
            it.copy(comms = it.comms.copy(recordProhibitedNumbers = set, recordMandatoryNumbers = mandatorySet))
        }
    }

    val recordMandatoryNumbers
        get() = dataStore.data.map { it.comms.recordMandatoryNumbers }
    fun setNumberRecordMandatory(number: String, mandatory: Boolean) {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isBlank()) return
        dataStore.update {
            val set = it.comms.recordMandatoryNumbers.toMutableSet()
            if (mandatory) set.add(clean) else set.remove(clean)
            val prohibitedSet = it.comms.recordProhibitedNumbers.toMutableSet()
            if (mandatory) prohibitedSet.remove(clean)
            it.copy(comms = it.comms.copy(recordMandatoryNumbers = set, recordProhibitedNumbers = prohibitedSet))
        }
    }

    val snapshot
        get() = dataStore.data.map { it.comms }

    fun replaceFromBackup(group: CommsGroup) {
        dataStore.update { it.copy(comms = group) }
    }
}
// === TELOS_PENDING_REVIEW_END: comms_settings_engine ===
