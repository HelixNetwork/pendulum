package net.helix.hlx.service;

import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.helix.hlx.TransactionValidator;
import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.service.snapshot.SnapshotProvider;


public class APITest {

    final String txHex = "bf443967e93cf9e5ec3a5b1fa029527deb45da2627e80050d45fc9f0003679055f1b6ca9f25ac70f44f09c4aacb155a2340cbff3043070e382ddb62da72aa650f76b0f8b494203204954053afa20aec27417ba3419b68236cdc538091c6a6cf1854ea1147ee2f39128a5b8cdbf6c30b0e9553f07499ee74dcf88b53ae08b11fdcc376b14c76bfcd90122c2deccbfbba1dcf46041f0d34c88f15f96c25ce902ff7d25e7ab4cb50f29304c2adf498de522973bf123f865a84c4386e886fc370377aafd1f5f45f4b95b21fa2dc6629f2e879ec80ccf658c4a128d335f83fd4e0746313a91f31cadcd0384c973401bd5d1008d8e3446fa906f6fa849f6cad209725d8fa076845d65787c0a51f9f97be5046c89f7c0c4b1805375e6b9140d2851dfbcb51b29842ea135cc9f853062f72c19b3f4a68c2363b3e8ff64086706383540f8a4f2155dbf9170d7cb1cec664d8d3421ea749d963852b867ed080633568aa7a502e693253b5d31fe2c6969ca589e4ba224b118b9362416a7dcb81343b77038792ce5f786a1b53d06c9e40c3be09e6268d9af6ed3d1b755f5304e489a8c229de68c4e47bd0cf9e47ad5ad42471bbf5dd2eaad857b7a515f28f450f119a35cfcb24217067d6252d9775925888b80e2a5be4cdabdc615cbe2aa561c68f0d8e5866c0201cfa66bd231ade44b197d7345ec5dc8f63c93cbaa2604ae4d5efe69eafcb0ce88b9c3fefb96b3245e44275013b58f95046e9af97c458064c47543fa028ef62475ae6c64d8e4cc36d9ea77ff4587618770f71911b4c7c4d955138446830087fe0ca66fc87a8dc8500334e7f9cbc67f28d844db7ad8a400140e16f2afbd6b64e22fb619e5c493af0a694b50c6b2dd651abaf812a516da7f74a3e8832e3ae14311ced1b0a5988e9751e904b890463a00d28098a2c2bd5b927b93a2d6da7623debf8499174638625dbb4f8ead72a346ba36212f20b8d09cf63b28bc8fc6116cf78ab349b0c8cdab6fc0e22abc7e1647b9f583343bf4873aebbf4d95898db3bc9fac7c3b5f6a16bfdfdeb3e11dfd4b8640c523afcfa6f4c8118746e41950e829da7bce0f244626d0036f3e2139f839f1d77a454de5bd0d72a8db671f45ee994c02e1edc3228201f4174d1ee059f7a7daeb18999a583574b8c426b30dfc85730e746fc8af5875a08f35a0b91199fe8b9f9706c469c9785e1d5ff20592bf4296b66d2ff7d0f38a8bf098714d62a460cc3b7a0a09a6883500941f31cdc6f3ba9d4322021e306a40f96e8d7a8f720fb94e724c3cdd5e2a982d848e608305846a5ae5a550f4caf27c45299c2435c31705cdf76071d3982f5ff66c348fe2026f90ab1452a13d7860fd32ec42e2e14bba0dc30ab0ece27a878b3209df1f58d261def6eb064edf8363369851fd1fe5f238255125369cfebe8c4b081c3dd4a0b8b1f5206e63f9073f88c5f5af47ef4797206c5c39d7413682d09f5a08a965b84a6f2fa8e87a4c76bd2f3e21ee60f2c8992d9ccfd19775fce735b7f45491fc7e2387de8e96234497f8d2f31c44f803a52fbb0aa94b0da9b30b441b39926e93f7f184d13e22f433073a022c6167c5ce3ecbba91982dec6ce9b3a03c605e5fd7480f8cfdfe2546068d67c0135a6a834f54d2ae66faa1b0faab32e2367849c2b70e207aed53608b6aac94cfe3786a79a8bf4d9051e3b7404a5e66758b2c8ee8e361a1e443dc06ee8e16c0fc4af13cb8467864b70458ca9390d512b694bd8f306526852f1602fc03";

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private TransactionValidator transactionValidator;

    @Mock
    private SnapshotProvider snapshotProvider;

    @Mock
    private HelixConfig config;

    @Test
    public void whenStoreTransactionsStatementThenSetArrivalTimeToCurrentMillis() throws Exception {
        TransactionViewModel transaction = mock(TransactionViewModel.class);
        when(transactionValidator.validateBytes(any(), anyInt())).thenReturn(transaction);
        when(transaction.store(any(), any())).thenReturn(true);

        API api = new API(config, null, null, null, null, null,
                snapshotProvider, null, null, null, null,
                transactionValidator, null, null, null, null);
        api.storeTransactionsStatement(Collections.singletonList(txHex));

        verify(transaction).setArrivalTime(longThat(
                new ArgumentMatcher() {
                    @Override
                    public boolean matches(Object arrival) {
                        long now = System.currentTimeMillis() / 1000;
                        return (Long)arrival > now - 1000 && (Long)arrival <= now;
                    }
                }
        ));
    }

}
