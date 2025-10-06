
package nl.mikekemmink.noodverlichting.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.stroom.StroomOverzichtActivity;

public class Nen3140Activity extends BaseToolbarActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.content_nen3140_cards);
        setTitle("NEN 3140");
        applyPalette(Palette.NEN);
        setUpEnabled(true);

        MaterialCardView cardInvoeren = findViewById(R.id.cardInvoeren);
        MaterialCardView cardOverzicht = findViewById(R.id.cardOverzicht);

        cardInvoeren.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Nen3140Activity.this, MeasurementsActivity.class));
            }
        });

        cardOverzicht.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Nen3140Activity.this, StroomOverzichtActivity.class));
            }
        });
    }
}
