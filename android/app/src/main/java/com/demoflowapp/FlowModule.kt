package com.demoflowapp

import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Color
import com.checkout.components.core.CheckoutComponentsFactory
import com.checkout.components.interfaces.Environment
import com.checkout.components.interfaces.component.CheckoutComponentConfiguration
import com.checkout.components.interfaces.error.CheckoutError
import com.checkout.components.interfaces.model.PaymentSessionResponse
import com.checkout.components.interfaces.component.ComponentCallback
import com.checkout.components.interfaces.model.ComponentName
import com.checkout.components.interfaces.api.CheckoutComponents
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlowModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private lateinit var checkoutComponents: CheckoutComponents
    private var containerView: FrameLayout? = null

    override fun getName(): String {
        return "FlowModule"
    }

    // Helper function to send events to JavaScript
    private fun sendEvent(eventName: String, params: WritableMap) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    @ReactMethod
    fun addListener(eventName: String?) {
        // Required for RN built-in EventEmitter to work
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for RN built-in EventEmitter to work
    }

    @ReactMethod
    fun startPaymentSession(paymentSessionID: String, paymentSessionToken: String, paymentSessionSecret: String) {
        Log.d("FlowModule", "Public Key: ${BuildConfig.FLOW_API_KEY}")
        Log.d("FlowModule", "startPaymentSession invoked with ID: $paymentSessionID")

        val context = currentActivity as? Context ?: return

        // Create containerView dynamically
        containerView = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Add containerView as an overlay view
        currentActivity?.runOnUiThread {
            currentActivity?.addContentView(containerView, containerView?.layoutParams)
            Log.d("FlowModule", "ContainerView added as overlay")
        }

        val customComponentCallback = ComponentCallback(
            onReady = { component ->
                Log.d("FlowModule", "Component ready: ${component.name}")
            },
            onSubmit = { component ->
                Log.d("FlowModule", "Component submitted: ${component.name}")
            },
            onSuccess = { component, paymentID ->
                Log.d("FlowModule", "Payment Success in component: ${component::class.java.simpleName}, ID: $paymentID")
                val map = Arguments.createMap().apply {
                    putString("component", component::class.java.simpleName)
                    putString("paymentId", paymentID)
                }
                sendEvent("onFlowPaymentSuccess", map)
            },
            onError = { component, checkoutError ->
                Log.e("FlowModule", "Error in component: ${component::class.java.simpleName}, ${checkoutError.message}")
                val map = Arguments.createMap().apply {
                    putString("component", component::class.java.simpleName)
                    putString("errorMessage", checkoutError.message ?: "Unknown error")
                    putString("errorCode", checkoutError.code.toString())
                }
                //sendEvent("onFlowPaymentError", map)
            }
        )

        val configuration = CheckoutComponentConfiguration(
            context = context,
            paymentSession = PaymentSessionResponse(
                id = paymentSessionID,
                paymentSessionToken = paymentSessionToken,
                paymentSessionSecret = paymentSessionSecret
            ),
            componentCallback = customComponentCallback,
            publicKey = BuildConfig.FLOW_API_KEY,
            environment = Environment.SANDBOX
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkoutComponents = CheckoutComponentsFactory(config = configuration).create()
                val flowComponent = checkoutComponents.create(ComponentName.Flow)

                withContext(Dispatchers.Main) {
                    // Ensure the containerView is non-null before passing it to provideView()
                    containerView?.let {
                        val view = flowComponent.provideView(it)
                        if (view != null) {
                            it.addView(view)
                        } else {
                            val fallbackTextView = TextView(context).apply {
                                text = "Flow component failed to render."
                                textSize = 20f
                                setTextColor(Color.RED)
                            }
                            it.addView(fallbackTextView)
                        }
                    }
                }

            } catch (checkoutError: CheckoutError) {
                Log.e("FlowModule", "Exception during component creation", checkoutError)
                checkoutError.printStackTrace()
                val errorParams = Arguments.createMap().apply {
                    putString("component", "Flow")
                    putString("errorMessage", checkoutError.message ?: "Unknown error")
                    putString("errorCode", checkoutError.code.toString())
                }
                //sendEvent("onFlowPaymentError", errorParams)
            }
        }
    }

    @ReactMethod
    fun dismissFlow() {
        currentActivity?.runOnUiThread {
            Log.d("FlowModule", "Dismissing Flow and returning to the initial view")

            // Remove the containerView from the current activity without using the resource ID
            containerView?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                Log.d("FlowModule", "ContainerView removed from the activity")
            }
        }
    }
}