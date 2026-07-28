# NFC demo tags

The `mocked` and `hybrid` Android builds accept an ordinary writable NFC tag with one NDEF URI record. The tag selects an in-app mock data set; it never supplies a private key, authentication data, or a real card response. In `GoogleHybrid`, an eligible NFC demo card profile can be bound to a wallet whose key material is owned by Hot SDK. The app keeps the simulated card UX while using the Hot SDK wallet for production balance, fee, signing, broadcast, and transaction-history paths.

## Build modes

| Build | Package | NFC/card flow | Wallet data and transactions | Intended use |
| --- | --- | --- | --- | --- |
| `GoogleMocked` | `com.tangem.wallet.mocked` | Local fixtures | Local fixtures; never broadcasts | Complete visual and failure-path testing |
| `GoogleHybrid` | `com.tangem.wallet.hybrid` | Automatic routing between ordinary-NFC demo fixtures and the real Tangem SDK | Production blockchain data; Hot SDK signing for a bound NFC demo wallet or a standalone mobile wallet | End-to-end NFC demo UX with disposable real assets, plus real-card regression testing |
| `GoogleRelease` | `com.tangem.wallet` | Real Tangem Card SDK | Production | Official card behavior |

Build the hybrid APK with:

```shell
./gradlew :app:assembleGoogleHybrid --no-configuration-cache --console=plain
```

The Hybrid build deliberately separates the two trust domains:

- The ordinary NFC tag only selects and advances mock card UI. It is not a signing factor and cannot authorize an on-chain transaction.
- Creating a wallet for an eligible NFC demo card generates or reuses a Hot SDK wallet. Only the opaque Hot SDK identifier and the resulting public wallet data are associated with the simulated card profile; seed words and private keys never leave Hot SDK storage.
- The simulated cold-wallet shell keeps the card-oriented product and onboarding UX. At transaction-signing time, the signer factory resolves its Hot SDK binding and delegates to the existing hot-wallet signer. The NFC tap is visual presence only and is not signing authorization.
- If the Hot SDK binding is absent or invalid, Hybrid uses a blocking signer and does not broadcast. It never falls back to fixture keys or treats the NDEF tag as a cryptographic signer.
- Setting the demo card access code also protects the associated Hot SDK wallet. Hot SDK unlock, not the NFC tag, is the signing authority.
- The current demo seed-import flow does not import the entered mnemonic into Hot SDK. It completes the card-import UI but generates or reuses the separately managed Hot SDK backing wallet.
- A standalone mobile wallet remains a normal software/hot wallet and uses the same production Hot SDK signing path without any NFC involvement.
- Real balance, quote, fee, transaction-history, and broadcast paths use production bindings because `MOCK_DATA_SOURCE=false`.
- If the bundled Ethereum history provider fails for a bound NFC demo wallet, Hybrid falls back to the keyless RouteScan Ethereum mainnet API. This exception is limited to NFC demo wallet IDs and Ethereum; real cards, standalone hot wallets, and other networks retain their normal providers.
- Visa/Tangem Pay onboarding remains local and visual-only for the dedicated NFC demo Visa wallet because an ordinary NDEF tag cannot implement the original card cryptography or payment-provider identity. A separately created/imported mobile wallet never receives or displays the demo payment account, balance, or card details.

Use only a disposable wallet and a very small balance while validating this modified debug-signed build. Never place a mnemonic, private key, access code, PIN, PAN, CVV, API credential, transaction signature, or authentication token in NFC content or test logs.

## Provisioning

1. Build and install either the mocked or hybrid APK.
2. Before each first-run scenario, clear that app's data so it has no stored user wallet.
3. Write an NDEF URI record to a test tag. The recommended Visa value is
   `https://www.tangem.com/ndef/demo/v1/visa`; `tangem-demo://v1/visa` remains an equivalent internal alias.
4. For reliable cold launch on Android, add an Android Application Record for `com.tangem.wallet.mocked` after the URI
   record. The physical tag used for device verification contains both records.
5. Launch the app by tapping the tag, or press the normal scan/add-wallet action and then hold the tag near the NFC
   antenna. The app opens its normal card-scan flow with the selected local mock content.

When an in-app demo operation is waiting, Android Reader Mode consumes the next tag discovery without changing routes.
It reads the NDEF scenario, verifies that the tag stays in range for about 1.5 seconds, and only then releases the local
fixture response. A tag discovered outside an active operation continues to use the normal NFC intent routing path.

The NFC intent filter exists only in `src/mocked/AndroidManifest.xml` and `src/hybrid/AndroidManifest.xml`; production, internal, external, and release APKs do not register this protocol.

## Physical scan outcomes

Every operation that starts a Tangem SDK card session uses the same physical gate. The tag is only a presence trigger;
none of its bytes are treated as a key, signature, PIN, access code, or authorization token. Phone-local access-code
cache maintenance does not require a tag because the production SDK does not start a card session for those operations.

| Action while the scan overlay is visible | Result |
| --- | --- |
| Hold the ordinary tag in range for about 1.5 seconds | `Success`; continue the selected local flow |
| Remove the tag before the presence checks finish | `TagLost`; show failure feedback and return to the previous screen |
| Press Cancel or use the system back action before discovery | `Cancelled`; return without advancing |
| Do not present a tag for 60 seconds | `Timeout`; show failure feedback and return without advancing |

Honor device verification covered all four outcomes. Structured evidence includes `reader_mode_enabled`,
`reader_mode_tag_discovered`, `physical_gate_reading`, `physical_gate_finished`, and `reader_mode_disabled` events.

## Card-session coverage

The following matrix is the code-level coverage contract for `GoogleHybrid`. Each `Physical gate` entry must be verified on
the target phone before the separate release-like NFC build is produced.

| Real-card workflow | Hybrid implementation | Physical gate |
| --- | --- | --- |
| Scan/add card and refresh card data | Local card profile selected by NDEF | Yes |
| Create card wallet | Generate or reuse a Hot SDK backing wallet and map its public keys into the simulated card response | Yes |
| Import seed phrase to card wallet | Complete the local import UI, but generate or reuse the Hot SDK backing wallet instead of importing the entered mnemonic | Yes |
| Derive public/extended public keys | Local deterministic derivation response | Yes |
| Change access code/passcode and recovery settings | Local success response | Yes |
| Reset card and reset a backup card | Local success response | Yes |
| Twin: create first, create second, finalize | Local Twin responses | Yes, once per SDK session |
| Backup: read primary and add backup card 1/2 | Local backup-service state machine | Yes, once per card |
| Backup: finalize primary and backup card 1/2 | Local backup-service state machine | Yes, once per card write |
| Visa activation and customer-wallet approval | Local visual signature fixtures | Yes, once per card-signing step |
| Tangem Pay credentials and withdrawal signature | Local visual signature fixtures | Yes |
| Bound NFC demo wallet transaction signing | Resolve the persisted Hot SDK binding and use `TangemHotWalletSigner` | NFC is not involved in authorization; real production broadcast path |
| Unbound or invalid NFC demo wallet transaction signing | Explicitly rejected by `NfcDemoColdWalletSigner` | No broadcast |
| Mobile-hot-wallet transaction signing | Existing Hot SDK signer | NFC is not involved; real production broadcast path |

The only direct SDK sessions outside `TangemSdkManager` are transaction signing and hardware backup. Transaction signing
is isolated by wallet origin and the presence of a valid Hot SDK binding. Hardware backup is routed through
`CardBackupService`: production builds delegate to the unchanged Tangem SDK service, while NFC-enabled builds use the
physical-gated local state machine.

## Supported scenarios

| URI | Mock content |
| --- | --- |
| `tangem-demo://v1/all` | Wallet 2 showcase |
| `tangem-demo://v1/wallet-2` | Wallet 2 showcase (explicit alias) |
| `tangem-demo://v1/wallet` | Wallet |
| `tangem-demo://v1/twins` | Twins |
| `tangem-demo://v1/ring` | Ring |
| `tangem-demo://v1/note` | Note |
| `tangem-demo://v1/wallet-2-no-backup` | Wallet 2 without backup |
| `tangem-demo://v1/wallet-2-empty` | Wallet 2 without backup or wallets |
| `tangem-demo://v1/wallet-2-seed` | Wallet 2 with a seed phrase |
| `tangem-demo://v1/wallet-2-derivations` | Wallet 2 with derivations |
| `tangem-demo://v1/shiba` | Shiba |
| `tangem-demo://v1/shiba-no-backup` | Shiba without backup |
| `tangem-demo://v1/shiba-empty` | Shiba without backup or wallets |
| `tangem-demo://v1/backup-wallet` | Backup wallet |
| `tangem-demo://v1/dev-wallet` | Development wallet |
| `tangem-demo://v1/firmware-4-12` | Firmware 4.12 wallet |
| `tangem-demo://v1/ed25519` | Ed25519 wallet |
| `tangem-demo://v1/secp256k1` | Secp256k1 wallet |
| `tangem-demo://v1/visa` | Visual-only Visa activation flow |
| `tangem-demo://v1/tag-lost` | Simulated tag-lost failure |

Card actions handled by the mock SDK manager return local responses only after a successful physical presence check. The
mocked APK must not be used for real assets, payments, or authentication. In the Hybrid APK, a simulated card profile can
use the real transaction path only after it has a valid Hot SDK backing-wallet binding. A standalone mobile wallet
continues to use its normal production transaction path. An unbound fixture wallet cannot sign or broadcast.

Wallet 2 fixture profiles also return local create/import responses, so both generated-wallet and seed-import onboarding
can reach their normal success screens. The `tag-lost` profile returns an SDK failure and displays the standard localized
error dialog instead of silently returning to Home.

## Transaction isolation

| Wallet and build | Signing result | Network behavior |
| --- | --- | --- |
| Ordinary-NFC cold wallet in `GoogleMocked` | Deterministic non-cryptographic fixture signature | Local visual success only; no status refresh, hash upload, or broadcast |
| Bound ordinary-NFC demo wallet in `GoogleHybrid` | Existing Hot SDK signer using the persisted backing-wallet identifier | Production balance, fee, signing, broadcast, and history paths |
| Unbound or invalid ordinary-NFC demo wallet in `GoogleHybrid` | Explicitly rejected | No broadcast; the normal transaction error UI is shown |
| Separately created/imported mobile wallet in `GoogleHybrid` | Existing Hot SDK signer | Production fee, balance, and broadcast flow |
| Real Tangem card in production builds | Real Card SDK signer | Production behavior |

The fee and wallet-manager layers bypass the legacy `DemoTransactionSender` only for card IDs recognized as NFC demo
cards. Existing non-NFC demo fixtures retain their original non-transactional protection.

## Ethereum transaction-history fallback

The production Ethereum history provider currently fails when its bundled Blockbook/NowNodes credential is unavailable.
After that provider returns a fetch error, `GoogleHybrid` uses
`NfcDemoEthereumTxHistoryProvider` only when all of the following are true:

- the selected wallet is a cold-wallet shell backed by an NFC demo card ID;
- the requested currency is the Ethereum mainnet coin;
- the normal Blockchain SDK history request has already failed.

The fallback calls RouteScan's keyless address-transactions endpoint, validates the Ethereum address, reads at most 50
transactions, and converts incoming/outgoing transfer direction and confirmation status into the app's transaction-history
model. It does not affect signing or broadcast and is not used for real Tangem cards, standalone hot wallets, tokens, or
non-Ethereum networks.

## Latest device verification

The 2026-07-27 Honor-device validation completed a real Ethereum mainnet transfer through the bound NFC demo wallet:

- transaction hash: `0xc434a8a2500b234d37ec8d7cb307c73747716f7bca9a5e52623f9ceec8fe993c`;
- value: `0.001 ETH`;
- block: `25622345`;
- receipt status: success;
- gas used: `21000`;
- effective gas price: `81212373 wei`;
- fee: `0.000001705459833 ETH`.

The device UI subsequently displayed both the outgoing `-0.001 ETH` record and the incoming `+0.01888242 ETH` record
through the RouteScan fallback. `NfcDemoEthereumTxHistoryProviderTest` passed 2/2 tests, and the Google Hybrid APK built
successfully with SHA-256
`50FA2E3D87A6CCCF2E27275101E11808676A9A2975F4CD605604F874611AC6D6`.

## Local Tangem Pay and Visa fixtures

The mocked APK uses in-process fixtures for the Tangem Pay and Visa paths selected by this demo. They do not use the mock API URL, WireMock, a payment provider, or an on-chain transaction.

| Area | Local behavior |
| --- | --- |
| Customer and card | An approved `mock-customer`, a EUR 250.00 balance, a virtual card ending in `4242`, and an unfrozen card state |
| Card details | Fixed PAN/CVV/expiry/PIN; freeze, unfreeze, card name, limits, and Add to Wallet update only in memory |
| Orders, reissue, withdrawal | Completed local order responses; a withdrawal success is visual-only and submits nothing |
| Transaction history | One fixed spend and one fixed payment entry |
| Visa activation/authentication | Fixed challenges, tokens, activation data, and success responses, all in process |
| KYC | A local approve/decline picker replaces the Sumsub SDK; it collects and transmits no identity data |

The KYC picker is a visual interaction fixture only: selecting either outcome does not alter the fixed mock-customer state. The `visa` tag drives an in-memory activation-state machine through access-code, customer-wallet confirmation, PIN, progress, and success screens. Force-stop the mocked app or clear its data before restarting that route. The regular demo tag can exercise local Tangem Pay card-detail, history, card-action, and success/failure UI fixtures.

## Test logs

The mocked build emits structured, non-sensitive events through two fixed Logcat tags. The same entries also pass through the app's existing file-log writer.

| Tag | Contents |
| --- | --- |
| `TangemNfcDemo` | NFC intent receipt, scenario selection, dispatch result, all mock SDK and backup card sessions, and card-signing stages |
| `TangemMockServer` | Local fixture request/response IDs, operation names, Visa activation states, KYC, orders, card actions, history, reissue, and withdrawal |

The fixture logs intentionally omit access/refresh tokens, PIN/CVV/PAN values, signatures, public keys, hashes, and wallet/card identifiers.

On Windows, capture a clean test session with:

```powershell
.\scripts\capture-mocked-nfc-logs.ps1 -Device 192.168.3.23:37009 -Clear
```

The command writes `build/logs/mocked-nfc.log` and continues until `Ctrl+C`. To print the currently buffered events once without starting a live capture:

```powershell
adb -s 192.168.3.23:37009 logcat -d -v threadtime "TangemNfcDemo:I" "TangemMockServer:I" "*:S"
```
