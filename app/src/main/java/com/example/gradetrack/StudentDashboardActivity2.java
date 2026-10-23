package com.example.gradetrack;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class StudentDashboardActivity2 extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        ImageButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);

        // Handle Back Button
        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Handle Logout Button
        btnLogout.setOnClickListener(v -> {
            Intent intent = new Intent(StudentDashboardActivity2.this, LoginActivity.class);
            // Clear backstack so user can't press back to return to dashboard
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}