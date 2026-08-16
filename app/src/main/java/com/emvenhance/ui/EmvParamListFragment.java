package com.emvenhance.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.emvenhance.R;
import com.pax.bizentity.db.helper.AmexAidDbHelper;
import com.pax.bizentity.db.helper.DpasAidDbHelper;
import com.pax.bizentity.db.helper.EFTAidDbHelper;
import com.pax.bizentity.db.helper.GreendaoHelper;
import com.pax.bizentity.db.helper.JcbAidDbHelper;
import com.pax.bizentity.db.helper.MirAidDbHelper;
import com.pax.bizentity.db.helper.PBOCAidDbHelper;
import com.pax.bizentity.db.helper.PaypassAidDbHelper;
import com.pax.bizentity.db.helper.PaywaveAidDbHelper;
import com.pax.bizentity.db.helper.PureAidDbHelper;
import com.pax.bizentity.db.helper.RupayAidDbHelper;
import com.pax.bizentity.entity.EmvAid;
import com.pax.bizentity.entity.EmvCapk;
import com.pax.bizentity.entity.clss.amex.AmexAidBean;
import com.pax.bizentity.entity.clss.dpas.DpasAidBean;
import com.pax.bizentity.entity.clss.eft.EFTAidBean;
import com.pax.bizentity.entity.clss.jcb.JcbAidBean;
import com.pax.bizentity.entity.clss.mir.MirAidBean;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;
import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean;
import com.pax.bizentity.entity.clss.pboc.PBOCAidBean;
import com.pax.bizentity.entity.clss.pure.PureAidBean;
import com.pax.bizentity.entity.clss.rupay.RupayAidBean;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows one EMV param category as a plain list — its own {@link Category} decides how to load
 * and render it. Loads only when this fragment's view is created, only its own category, never
 * the whole {@code EmvProcessParam} at once.
 */
public class EmvParamListFragment extends Fragment {

    private static final String ARG_CATEGORY = "category";

    public static EmvParamListFragment newInstance(Category category) {
        EmvParamListFragment fragment = new EmvParamListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, category.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_emv_param_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        Category category = Category.valueOf(
                args != null ? args.getString(ARG_CATEGORY) : Category.AID.name());

        ProgressBar progress = view.findViewById(R.id.progress);
        ListView list = view.findViewById(R.id.list);
        TextView emptyText = view.findViewById(R.id.emptyText);

        new Thread(() -> {
            List<String> rows;
            try {
                rows = new ArrayList<>();
                for (Object item : category.loader.load()) {
                    rows.add(category.renderer.render(item));
                }
            } catch (Exception e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
                rows = null;
            }
            List<String> finalRows = rows;
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                if (finalRows == null) {
                    emptyText.setText(R.string.emv_param_load_failed);
                    emptyText.setVisibility(View.VISIBLE);
                } else if (finalRows.isEmpty()) {
                    emptyText.setText(R.string.emv_param_empty);
                    emptyText.setVisibility(View.VISIBLE);
                } else {
                    list.setAdapter(new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_list_item_1, finalRows));
                }
            });
        }, "EmvParamList-" + category.name()).start();
    }

    private interface Loader {
        List<?> load();
    }

    private interface Renderer {
        String render(Object item);
    }

    /** One EMV param category: how to load it, and how to render each row. */
    public enum Category {
        AID("AID",
                () -> GreendaoHelper.getEmvAidHelper().loadAll(),
                item -> row(((EmvAid) item).getAppName(), ((EmvAid) item).getAid())),
        CAPK("CAPK",
                () -> GreendaoHelper.getEmvCapkHelper().loadAll(),
                item -> "RID " + ((EmvCapk) item).getRID() + "   Key " + ((EmvCapk) item).getKeyID()),
        AMEX("Amex",
                () -> AmexAidDbHelper.getInstance().loadAll(),
                item -> row(((AmexAidBean) item).getAppName(), ((AmexAidBean) item).getAid())),
        PAYPASS("PayPass",
                () -> PaypassAidDbHelper.getInstance().loadAll(),
                item -> row(((PayPassAidBean) item).getAppName(), ((PayPassAidBean) item).getAid())),
        PAYWAVE("PayWave",
                () -> PaywaveAidDbHelper.getInstance().loadAll(),
                item -> row(((PaywaveAidBean) item).getAppName(), ((PaywaveAidBean) item).getAid())),
        DPAS("Dpas",
                () -> DpasAidDbHelper.getInstance().loadAll(),
                item -> row(((DpasAidBean) item).getAppName(), ((DpasAidBean) item).getAid())),
        EFT("EFT",
                () -> EFTAidDbHelper.getInstance().loadAll(),
                item -> row(((EFTAidBean) item).getAppName(), ((EFTAidBean) item).getAid())),
        JCB("Jcb",
                () -> JcbAidDbHelper.getInstance().loadAll(),
                item -> row(((JcbAidBean) item).getAppName(), ((JcbAidBean) item).getAid())),
        MIR("Mir",
                () -> MirAidDbHelper.getInstance().loadAll(),
                item -> row(((MirAidBean) item).getAppName(), ((MirAidBean) item).getAid())),
        PBOC("Pboc",
                () -> PBOCAidDbHelper.getInstance().loadAll(),
                item -> row(((PBOCAidBean) item).getAppName(), ((PBOCAidBean) item).getAid())),
        PURE("Pure",
                () -> PureAidDbHelper.getInstance().loadAll(),
                item -> row(((PureAidBean) item).getAppName(), ((PureAidBean) item).getAid())),
        RUPAY("RuPay",
                () -> RupayAidDbHelper.getInstance().loadAll(),
                item -> row(((RupayAidBean) item).getAppName(), ((RupayAidBean) item).getAid()));

        public final String label;
        private final Loader loader;
        private final Renderer renderer;

        Category(String label, Loader loader, Renderer renderer) {
            this.label = label;
            this.loader = loader;
            this.renderer = renderer;
        }
    }

    private static String row(String appName, String aid) {
        return appName + "   (" + aid + ")";
    }
}
