/*

   Copyright 2015 Andreas Würl

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

package org.blitzortung.android.location

import android.content.*
import android.location.Location
import android.location.LocationManager
import android.util.Log
import android.widget.Toast
import org.blitzortung.android.app.Main
import org.blitzortung.android.app.R
import org.blitzortung.android.app.view.OnSharedPreferenceChangeListener
import org.blitzortung.android.app.view.PreferenceKey
import org.blitzortung.android.app.view.get
import org.blitzortung.android.location.provider.LocationProvider
import org.blitzortung.android.location.provider.createLocationProvider
import org.blitzortung.android.protocol.ConsumerContainer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationHandler @Inject constructor(
    private val context: Context,
    sharedPreferences: SharedPreferences
) : OnSharedPreferenceChangeListener {

    init {
        Log.v(Main.LOG_TAG, "LocationHandler() init")
    }

    var location: Location? = null
        private set

    var backgroundMode = true
        private set(value) {
            field = value
            updateProvider()
        }

    private var provider: LocationProvider? = null

    private val consumerContainer = object : ConsumerContainer<LocationEvent>() {
        override fun addedFirstConsumer() {
            provider?.run {
                if (!isRunning) {
                    start()
                }
            }
        }

        override fun removedLastConsumer() {
            provider?.run {
                if (isRunning) {
                    shutdown()
                }
            }
        }
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        onSharedPreferenceChanged(sharedPreferences, PreferenceKey.LOCATION_MODE)

        //We need to know when a LocationProvider is enabled/disabled
        val iFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(LocationProviderChangedReceiver(), iFilter)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: PreferenceKey) {
        when (key) {
            PreferenceKey.LOCATION_MODE -> {
                val providerFactory = { type: String ->
                    createLocationProvider(context, backgroundMode, { location -> sendLocationUpdate(location) }, type)
                }

                val newProvider = providerFactory(sharedPreferences.get(key, LocationManager.PASSIVE_PROVIDER))

                if (provider?.type != newProvider.type && provider?.isRunning == true) {
                    enableNewProvider(newProvider)
                } else {
                    provider = newProvider
                }
            }

            else -> {}
        }
    }

    private fun enableNewProvider(newProvider: LocationProvider) {
        Log.v(Main.LOG_TAG, "LocationHandler.enableProvider(${newProvider.type})")
        //If the current provider is not null and is Running, shut it down first
        provider?.let { provider ->
            if (provider.isRunning) {
                provider.shutdown()
            }
        }

        //TODO we need to tell the UI if the locationProvider is stopped/started
        //Now start the new provider if it is enabled
        this.provider = newProvider.apply {
            if (!this.isEnabled) {
                val message = context.resources.getString(R.string.location_provider_disabled).format(newProvider.type)
                Toast.makeText(context, message, Toast.LENGTH_LONG)
            } else {
                start()
            }
        }
    }

    private fun sendLocationUpdate(location: Location?) {
        val location = location?.takeIf { it.isValid }
        this.location = location

        Log.i(Main.LOG_TAG, "LocationHandler.sendLocationUpdate() location $location")
        val event = LocationEvent(location)
        if (location != null) {
            consumerContainer.storeAndBroadcast(event)
        } else {
            consumerContainer.broadcast(event)
        }
    }

    fun requestUpdates(locationConsumer: (LocationEvent) -> Unit) {
        consumerContainer.addConsumer(locationConsumer)
    }

    fun removeUpdates(locationEventConsumer: (LocationEvent) -> Unit) {
        consumerContainer.removeConsumer(locationEventConsumer)
    }

    private val Location.isValid: Boolean
        get() = !java.lang.Double.isNaN(longitude) && !java.lang.Double.isNaN(latitude)

    fun enableBackgroundMode() {
        backgroundMode = true
    }

    fun disableBackgroundMode() {
        backgroundMode = false
    }

    private fun updateProvider() {
        provider?.run {
            //Reconfigure the Provider only, when its running
            if (isRunning) {
                reconfigureProvider(this@LocationHandler.backgroundMode)
            }
        }
    }

    fun shutdown() {
        provider?.run {
            if (isRunning) {
                shutdown()
            } else {
                Log.v(Main.LOG_TAG, "LocationHandler.shutdown() provider $type already stopped")
            }
        }
    }

    fun start() {
        provider?.run {
            if (!isRunning) {
                start()
            } else {
                Log.v(Main.LOG_TAG, "LocationHandler.start() provider $type already running")
            }
        }
    }

    fun update(preferences: SharedPreferences) {
        onSharedPreferenceChanged(preferences, PreferenceKey.LOCATION_MODE)
    }

    private inner class LocationProviderChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            //TODO we need to tell the UI if the locationProvider is stopped/started

            provider?.run {
                if (!this.isRunning && this.isEnabled) {
                    start()
                } else if (this.isRunning && !this.isEnabled) {
                    shutdown()
                }
            }
        }
    }

    companion object {
        const val MANUAL_PROVIDER = "manual"
    }

}
