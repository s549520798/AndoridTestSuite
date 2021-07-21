package priv.lzy.andtestsuite.fragment

import android.content.Context
import android.os.Bundle
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import priv.lzy.andtestsuite.utils.LogUtil
import priv.lzy.andtestsuite.viewmodels.SettingViewModel

class SettingFragment: Fragment() {

    var preferences: BaseCatalogPreferences? = null
    private val mTag: String = this.javaClass.simpleName

    private val mSettingViewModel: SettingViewModel by viewModels()

    private val buttonIdToOptionId = SparseIntArray()

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onCreateView(
        layoutInflater: LayoutInflater,
        viewGroup: ViewGroup?,
        bundle: Bundle?
    ): View {
        LogUtil.d(mTag, "onCreateView")
        val container = layoutInflater.inflate(
            R.layout.mtrl_preferences_dialog, viewGroup, false
        ) as LinearLayout
        for (catalogPreference in preferences.getPreferences()) {
            container.addView(createPreferenceView(layoutInflater, container, catalogPreference))
        }
        return container
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogUtil.d(mTag, "onViewCreated")
    }

    private fun createPreferenceView(
        layoutInflater: LayoutInflater,
        rootView: ViewGroup,
        preference: CatalogPreference
    ): View? {
        val isEnabled: Boolean = preference.isEnabled()
        val view: View =
            layoutInflater.inflate(R.layout.mtrl_preferences_dialog_preference, rootView, false)
        val description = view.findViewById<TextView>(R.id.preference_description)
        description.isEnabled = isEnabled
        description.setText(preference.description)
        val buttonToggleGroup: MaterialButtonToggleGroup =
            view.findViewById(R.id.preference_options)
        val selectedOptionId: Int = preference.getSelectedOption(view.context).id
        for (option in preference.getOptions()) {
            val button = createOptionButton(layoutInflater, buttonToggleGroup, option)
            button.isEnabled = isEnabled
            buttonToggleGroup.addView(button)
            button.isChecked = selectedOptionId == option.id
        }
        buttonToggleGroup.isEnabled = isEnabled
        if (isEnabled) {
            buttonToggleGroup.addOnButtonCheckedListener { group: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean ->
                if (isChecked) {
                    preference.setSelectedOption(
                        group.context,
                        buttonIdToOptionId[checkedId]
                    )
                }
            }
        }
        return view
    }

    private fun createOptionButton(
        layoutInflater: LayoutInflater,
        rootView: ViewGroup,
        option: Option
    ): MaterialButton {
        val button = layoutInflater.inflate(
            R.layout.mtrl_preferences_dialog_option_button, rootView, false
        ) as MaterialButton
        val buttonId = ViewCompat.generateViewId()
        buttonIdToOptionId.append(buttonId, option.id)
        button.id = buttonId
        button.setIconResource(option.icon)
        button.setText(option.description)
        return button
    }
}