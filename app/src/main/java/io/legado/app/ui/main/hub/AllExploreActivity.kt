package io.legado.app.ui.main.hub

import android.os.Bundle
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivitySubscriptionsBinding
import io.legado.app.ui.main.explore.ExploreFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AllExploreActivity : BaseActivity<ActivitySubscriptionsBinding>() {
    override val binding by viewBinding(ActivitySubscriptionsBinding::inflate)
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(R.id.subscriptions_container, ExploreFragment(0)).commit()
    }
}
