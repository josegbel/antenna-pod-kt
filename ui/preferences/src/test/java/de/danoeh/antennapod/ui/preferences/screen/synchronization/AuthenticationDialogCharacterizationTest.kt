package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.DialogInterface
import android.os.Looper
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import de.danoeh.antennapod.ui.preferences.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AuthenticationDialogCharacterizationTest {

    private fun buildDialog(
        enableUsernameField: Boolean = true,
        usernameInitialValue: String? = "existingUser",
        passwordInitialValue: String? = null,
        onConfirmed: (String, String) -> Unit = { _, _ -> },
        onCancelled: () -> Unit = {}
    ): AlertDialog {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val dialog = object : AuthenticationDialog(
            activity,
            android.R.string.ok,
            enableUsernameField,
            usernameInitialValue,
            passwordInitialValue
        ) {
            override fun onCancelled() {
                onCancelled.invoke()
            }

            override fun onConfirmed(username: String, password: String) {
                onConfirmed.invoke(username, password)
            }
        }.show()
        return dialog
    }

    @Test
    fun testPasswordToggleSwapsTransformationAndAlpha() {
        val dialog = buildDialog()
        val passwordEditText = dialog.findViewById<EditText>(R.id.passwordEditText)!!
        val showPasswordButton = dialog.findViewById<ImageView>(R.id.showPasswordButton)!!

        assertEquals(0.6f, showPasswordButton.alpha)

        showPasswordButton.performClick()
        assertTrue(passwordEditText.transformationMethod is HideReturnsTransformationMethod)
        assertEquals(1.0f, showPasswordButton.alpha)

        showPasswordButton.performClick()
        assertTrue(passwordEditText.transformationMethod is PasswordTransformationMethod)
        assertEquals(0.6f, showPasswordButton.alpha)
    }

    @Test
    fun testUsernameFieldEnabledStateFromConstructor() {
        val enabledDialog = buildDialog(enableUsernameField = true)
        val enabledField = enabledDialog.findViewById<EditText>(R.id.usernameEditText)!!
        assertTrue(enabledField.isEnabled)

        val disabledDialog = buildDialog(enableUsernameField = false)
        val disabledField = disabledDialog.findViewById<EditText>(R.id.usernameEditText)!!
        assertFalse(disabledField.isEnabled)
    }

    @Test
    fun testNullInitialValuesLeaveFieldsUntouched() {
        // Neither field is seeded, and construction must not crash if the null-check guard
        // that makes this safe (AuthenticationDialog.java:25-30) is dropped during conversion.
        val dialog = buildDialog(usernameInitialValue = null, passwordInitialValue = null)
        val usernameField = dialog.findViewById<EditText>(R.id.usernameEditText)!!
        val passwordField = dialog.findViewById<EditText>(R.id.passwordEditText)!!
        assertEquals("", usernameField.text.toString())
        assertEquals("", passwordField.text.toString())

        // A non-null value, by contrast, must actually be seeded -- this is what discriminates
        // "guarded by an if" from "the field is always empty regardless of the argument".
        val seededDialog = buildDialog(usernameInitialValue = "someone", passwordInitialValue = "secret")
        val seededUsername = seededDialog.findViewById<EditText>(R.id.usernameEditText)!!
        val seededPassword = seededDialog.findViewById<EditText>(R.id.passwordEditText)!!
        assertEquals("someone", seededUsername.text.toString())
        assertEquals("secret", seededPassword.text.toString())
    }

    @Test
    fun testOnCancelledFiresFromNegativeButtonAndFromCancelListener() {
        var negativeButtonCancelCount = 0
        val negativeDialog = buildDialog(onCancelled = { negativeButtonCancelCount++ })
        negativeDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, negativeButtonCancelCount)

        var cancelListenerCancelCount = 0
        val cancelledDialog = buildDialog(onCancelled = { cancelListenerCancelCount++ })
        cancelledDialog.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, cancelListenerCancelCount)
    }
}
