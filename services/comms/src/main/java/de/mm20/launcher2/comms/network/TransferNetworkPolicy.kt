package de.mm20.launcher2.comms.network

/** Metering and roaming are additional restrictions, independent of transport. */
data class TransferNetworkPolicy(
    val wifi: Boolean = true,
    val mobile: Boolean = true,
    val roaming: Boolean = true,
    val metered: Boolean = true,
) {
    fun allows(online: Boolean, wifi: Boolean, mobile: Boolean, roaming: Boolean, metered: Boolean): Boolean =
        online && ((wifi || mobile) || (this.wifi && this.mobile && this.roaming)) && (!wifi || this.wifi) && (!mobile || this.mobile) &&
            (!roaming || this.roaming) && (!metered || this.metered)
}
