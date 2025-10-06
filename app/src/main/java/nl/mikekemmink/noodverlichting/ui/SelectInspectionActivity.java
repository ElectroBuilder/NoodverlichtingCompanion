
package nl.mikekemmink.noodverlichting.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.Nen3140Activity;
import nl.mikekemmink.noodverlichting.noodverlichting.NoodverlichtingActivity;

public class SelectInspectionActivity extends BaseToolbarActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.content_select_inspection_cards);
        setTitle("Kies inspectie");
        applyPalette(Palette.NEN); // of Palette.NEUTRAL als je neutraal wilt
        setUpEnabled(false);

        MaterialCardView cardNood = findViewById(R.id.cardNood);
        MaterialCardView cardNen  = findViewById(R.id.cardNen);

        cardNood.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SelectInspectionActivity.this, NoodverlichtingActivity.class));
            }
        });
        cardNen.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SelectInspectionActivity.this, Nen3140Activity.class));
            }
        });
    }
}
