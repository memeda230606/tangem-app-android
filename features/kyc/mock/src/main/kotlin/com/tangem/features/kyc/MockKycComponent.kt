package com.tangem.features.kyc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tangem.core.decompose.context.AppComponentContext
import com.tangem.features.kyc.impl.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** Local KYC outcome picker used by the mocked build without launching the Sumsub SDK. */
@Suppress("UnusedPrivateProperty")
internal class MockKycComponent @AssistedInject constructor(
    @Assisted appComponentContext: AppComponentContext,
    @Assisted params: KycComponent.Params,
) : KycComponent, AppComponentContext by appComponentContext {

    @Composable
    override fun Content(modifier: Modifier) {
        var result by remember { mutableStateOf<Result?>(null) }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.mock_kyc_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                text = stringResource(
                    when (result) {
                        null -> R.string.mock_kyc_description
                        Result.Approved -> R.string.mock_kyc_approved
                        Result.Rejected -> R.string.mock_kyc_rejected
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
            )

            if (result == null) {
                Button(onClick = { result = Result.Approved }) {
                    Text(stringResource(R.string.mock_kyc_approve))
                }
                Button(
                    modifier = Modifier.padding(top = 12.dp),
                    onClick = { result = Result.Rejected },
                ) {
                    Text(stringResource(R.string.mock_kyc_reject))
                }
            } else {
                Button(onClick = router::pop) {
                    Text(stringResource(R.string.mock_kyc_back_to_card))
                }
            }
        }
    }

    @AssistedFactory
    interface Factory : KycComponent.Factory {
        override fun create(context: AppComponentContext, params: KycComponent.Params): MockKycComponent
    }

    private enum class Result {
        Approved,
        Rejected,
    }
}
