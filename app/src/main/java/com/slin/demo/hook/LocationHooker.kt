package com.slin.demo.hook

import android.location.GnssStatus
import android.location.GpsSatellite
import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.location.OnNmeaMessageListener
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CancellationSignal
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.DoubleType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import com.highcapable.yukihookapi.hook.type.java.UnitType
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.concurrent.timer
import kotlin.random.Random
import kotlin.reflect.jvm.isAccessible

// 仙桃数据谷位置信息,WGS84
const val XIANTAO_LAT = 29.747119837570498
const val XIANTAO_LNG = 106.55219460941737

object LocationHooker : YukiBaseHooker() {
    private const val TAG = "Location Hook"

    private const val satellites = 20

    override fun onHook() {
        invalidateOthers()
        hookGPS()
        hookLocation()
    }

    /**
     * Make network and cell providers invalid
     */
    private fun invalidateOthers() {
        classOf<WifiManager>().hook {
            injectMember {
                method {
                    name = "getScanResults"
                    emptyParam()
                    returnType = classOf<List<ScanResult>>()
                }
                afterHook {
                    result = emptyList<ScanResult>()
                }
            }

            injectMember {
                method {
                    name = "getWifiState"
                    emptyParam()
                    returnType = IntType
                }
                replaceTo(WifiManager.WIFI_STATE_ENABLED)
            }

            injectMember {
                method {
                    name = "isWifiEnabled"
                    emptyParam()
                    returnType = BooleanType
                }
                replaceTo(true)
            }

            injectMember {
                method {
                    name = "getConnectionInfo"
                    emptyParam()
                    returnType = classOf<WifiInfo>()
                }
                replaceTo(null)
            }
        }

        classOf<NetworkInfo>().hook {
            injectMember {
                method {
                    name = "isConnectedOrConnecting"
                    emptyParam()
                    returnType = BooleanType
                }
                replaceTo(true)
            }

            injectMember {
                method {
                    name = "isConnected"
                    emptyParam()
                    returnType = BooleanType
                }
                replaceTo(true)
            }

            injectMember {
                method {
                    name = "isAvailable"
                    emptyParam()
                    returnType = BooleanType
                }
                replaceTo(true)
            }
        }

        classOf<WifiInfo>().hook {
            injectMember {
                method {
                    name = "getSSID"
                    emptyParam()
                    returnType = StringClass
                }
                replaceTo("null")
            }

            injectMember {
                method {
                    name = "getBSSID"
                    emptyParam()
                    returnType = StringClass
                }
                replaceTo("00-00-00-00-00-00-00-00")
            }

            injectMember {
                method {
                    name = "getMacAddress"
                    emptyParam()
                    returnType = StringClass
                }
                replaceTo("00-00-00-00-00-00-00-00")
            }
        }
    }

    private fun hookGPS() {
        val classOfLM = classOf<LocationManager>()
        classOfLM.hook {
            injectMember {
                method {
                    name = "getLastKnownLocation"
                    param(StringClass, "android.location.LastLocationRequest".toClass())
                    returnType = classOf<Location>()
                }
                replaceAny {
                    loggerD(TAG, "getLastKnownLocation $this")
                }
            }

            injectMember {
                method {
                    name = "getLastLocation"
                    emptyParam()
                    returnType = classOf<Location>()
                }
                replaceAny {
                    loggerD(TAG, "getLastLocation $this")
                }
            }

            injectMember {
                val updateMethods = classOfLM.methods
                    .filter {
                        it.name == "requestLocationUpdates"
                                || it.name == "requestSingleUpdate"
                    }
                    .toTypedArray()
                members(*updateMethods)
                replaceAny {
                    loggerD(TAG, "requestLocationUpdates || requestSingleUpdate $this")
                    if (satellites <= 0) return@replaceAny callOriginal()

                    val listener = args.firstOrNull { it is LocationListener } as LocationListener?
                        ?: return@replaceAny callOriginal()
                    val provider =
                        args.firstOrNull { it is String } as String? ?: LocationManager.GPS_PROVIDER

                    timer(name = "location changed", period = 1000) {
                        listener.onLocationChanged(Location(provider).apply {
                            this.latitude = XIANTAO_LAT
                            this.longitude = XIANTAO_LNG
                        })
                    }
                    listener.onLocationChanged(Location(provider).apply {
                        this.latitude = XIANTAO_LAT
                        this.longitude = XIANTAO_LNG
                    })
                }
            }

            injectMember {
                method {
                    name = "removeUpdates"
                    param(classOf<LocationListener>())
                }
                replaceAny {
                    val listener = args(0)
                    loggerD(TAG, "removeUpdates $this")
                }
            }

            // make the app believe gps works
            injectMember {
                method {
                    name = "getGpsStatus"
                    param(classOf<GpsStatus>())
                    returnType = classOf<GpsStatus>()
                }
                afterHook {
                    loggerD(TAG, "getGpsStatus $this")
                    val info = args(0).cast<GpsStatus>() ?: result as GpsStatus
                    val method7 =
                        GpsStatus::class.members.firstOrNull { it.name == "setStatus" && it.parameters.size == 8 }

                    if (method7 != null) {
                        method7.isAccessible = true


                        val prns = IntArray(satellites) { it }
                        val ones = FloatArray(satellites) { 1f }
                        val zeros = FloatArray(satellites) { 0f }
                        val ephemerisMask = 0x1f
                        val almanacMask = 0x1f

                        //5 Scheduler.satellites are fixed
                        val usedInFixMask = 0x1f

                        method7.call(
                            info,
                            satellites,
                            prns,
                            ones,
                            zeros,
                            zeros,
                            ephemerisMask,
                            almanacMask,
                            usedInFixMask
                        )
                    } else {
                        val method =
                            GpsStatus::class.members.firstOrNull { it.name == "setStatus" && it.parameters.size == 3 }
                        if (method == null) {
                            loggerE(TAG, "method GpsStatus::setStatus is not provided")
                            return@afterHook
                        }
                        method.isAccessible = true
                        val fake = fakeGnssStatus
                        method.call(info, fake, 1000 + Random.nextInt(-500, 500))
                    }
                    result = info
                }
            }

            injectMember {
                method {
                    name = "addGpsStatusListener"
                    param(classOf<GpsStatus.Listener>())
                    returnType = BooleanType
                }

                replaceAny {
                    loggerD(TAG, "addGpsStatusListener $this")
                    if (satellites <= 0) {
                        return@replaceAny callOriginal()
                    }
                    val listener = args(0).cast<GpsStatus.Listener>()
                    listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_STARTED)
                    listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_FIRST_FIX)
                    timer(name = "satellite heartbeat", period = 1000) {
                        listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_SATELLITE_STATUS)
                    }
                    true
                }
            }

            injectMember {
                method {
                    name = "addNmeaListener"
                    param(classOf<GpsStatus.NmeaListener>())
                    returnType = BooleanType
                }

                replaceAny {
                    loggerD(TAG, "addNmeaListener $this")
                    if (satellites <= 0) {
                        return@replaceAny callOriginal()
                    }
                    false
                }
            }

            injectMember {
                method {
                    name = "addNmeaListener"
                    param(classOf<Executor>(), classOf<OnNmeaMessageListener>())
                    returnType = BooleanType
                }

                replaceAny {
                    loggerD(TAG, "addNmeaListener $this")
                    if (satellites <= 0) {
                        return@replaceAny callOriginal()
                    }
                    false
                }
            }

            injectMember {
                method {
                    name = "registerGnssStatusCallback"
                    returnType = BooleanType
                }.all()
                replaceAny {
                    loggerD(TAG, "registerGnssStatusCallback $this")
                    if (satellites <= 0) {
                        return@replaceAny callOriginal()
                    }

                    val callback = if (args[0] is GnssStatus.Callback) {
                        args(0).cast<GnssStatus.Callback>()
                    } else {
                        args(1).cast<GnssStatus.Callback>()
                    }
                    callback?.onStarted()
                    callback?.onFirstFix(1000 + Random.nextInt(-500, 500))
                    timer(name = "satellite heartbeat", period = 1000) {
                        callback?.onSatelliteStatusChanged(fakeGnssStatus ?: return@timer)
                    }
                    true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                injectMember {
                    method {
                        name = "getCurrentLocation"
                        param(
                            StringClass,
                            classOf<LocationRequest>(),
                            classOf<CancellationSignal>(),
                            classOf<Executor>(),
                            classOf<Consumer<Location>>()
                        )
                        returnType = UnitType
                    }
                    replaceAny {
                        loggerD(TAG, "getCurrentLocation $this")
                        args(4).cast<Consumer<Location>>()
                            ?.accept(
//                                Scheduler.location.android(args(0).string(), estimatedSpeed, MapProjector)
                                Location(LocationManager.GPS_PROVIDER)
                            )
                    }
                }
            }
        }

        classOf<GpsStatus>().hook {
            injectMember {
                method {
                    name = "getSatellites"
                    emptyParam()
                    returnType = classOf<Iterable<GpsSatellite>>()
                }

                afterHook {
                    loggerD(TAG, "getSatellites $this")
                    if (satellites <= 0) return@afterHook

                    result = fakeSatellites.also {
                        loggerD(TAG, "${it.count()} satellites are fixed")
                    }
                }
            }

            injectMember {
                method {
                    name = "getMaxSatellites"
                    emptyParam()
                    returnType = IntType
                }

                replaceAny {
                    loggerD(TAG, "getMaxSatellites $this")
                    if (satellites <= 0) callOriginal()
                    else satellites
                }
            }
        }

        classOf<GnssStatus>().hook {
            injectMember {
                method {
                    name = "usedInFix"
                    param(IntType)
                    returnType = BooleanType
                }

                replaceAny {
                    loggerD(TAG, "usedInFix $this")
                    if (satellites <= 0) callOriginal()
                    else true
                }
            }
        }
    }

    private fun hookLocation() {
        fun YukiMemberHookCreator.common() {
            injectMember {
                method {
                    name = "getLatitude"
                    emptyParam()
                    returnType = DoubleType
                }

                afterHook {
                    loggerD(TAG, "slin getLatitude ${this.result}")
                    result = XIANTAO_LAT

                }
            }

            injectMember {
                method {
                    name = "getLongitude"
                    emptyParam()
                    returnType = DoubleType
                }

                afterHook {
                    loggerD(TAG, "slin getLongitude ${this.result}")
                    result = XIANTAO_LNG
                }
            }
        }

        classOf<Location>().hook {
            common()
        }
    }

    val fakeGnssStatus: GnssStatus?
        get() {
            val svid = IntArray(satellites) { it }
            val zeros = FloatArray(satellites) { 0f }
            val ones = FloatArray(satellites) { 1f }

            val constructor = GnssStatus::class.constructors.firstOrNull { it.parameters.size >= 6 }
            if (constructor == null) {
                loggerE(TAG, "GnssStatus constructor not available")
                return null
            }

            val constructorArgs = Array(constructor.parameters.size) { index ->
                when (index) {
                    0 -> satellites
                    1 -> svid
                    2 -> ones
                    else -> zeros
                }
            }
            return constructor.call(*constructorArgs)
        }

    private val fakeSatellites: Iterable<GpsSatellite> =
        buildList {
            val clz = classOf<GpsSatellite>()
            for (i in 1..satellites) {
                val instance =
                    clz.constructor {
                        param(IntType)
                    }
                        .get()
                        .newInstance<GpsSatellite>(i) ?: return@buildList
                listOf("mValid", "mHasEphemeris", "mHasAlmanac", "mUsedInFix").forEach {
                    clz.field { name = it }.get(instance).setTrue()
                }
                listOf("mSnr", "mElevation", "mAzimuth").forEach {
                    clz.field { name = it }.get(instance).set(1F)
                }
                add(instance)
            }
        }
}
