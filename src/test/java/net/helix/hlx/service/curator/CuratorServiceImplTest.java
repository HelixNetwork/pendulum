package net.helix.hlx.service.curator;

import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
//import net.helix.hlx.service.curator.impl.CuratorServiceImpl;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.snapshot.impl.SnapshotMockUtils;
import net.helix.hlx.storage.Tangle;
import org.bouncycastle.util.encoders.Hex;
import org.junit.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashSet;
import java.util.Set;

public class CuratorServiceImplTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private Set<Hash> mockedSeenCandidates = new HashSet<>();
    private Hash candidateAddress = HashFactory.ADDRESS.create("ee1c15a76b2b1ce72acd7e559afafb7418ffac15246d7c2c9d1bfe0ea4b6a924");

    @Mock
    private Tangle tangle;
    @Mock
    private SnapshotProvider snapshotProvider;
    @InjectMocks
    //private CuratorServiceImpl curatorService;

    private byte[] txBytes = Hex.decode("0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ee1c15a76b2b1ce72acd7e559afafb7418ffac15246d7c2c9d1bfe0ea4b6a92400000000000000000000000000000000000000000000000000000000000000000000000000000000000000005d092fc0000000000000000000000000000000005031b48d241283c312c68c777bc4563ddd7cbe1ae6a2c58079e1bf3cfef826790098c664bf3a5d2b915011ccb7e62ad46c537dedc89f6d4582d1bcf9bf23d54a0098c664bf3a5d2b915011ccb7e62ad46c537dedc89f6d4582d1bcf9bf23d54a68656c6c6f68656c0000000000000000000000000000000000000000000000000000016b6be287840000000000000000000000000000007f00000000000091b0");

    private enum MockedCandidate {
        A("41fa65373067b1d11fa4e14b3d92f39a6e570221143103dd1c1092b4cd2b7612", 70001),
        B("000000000000b1d11fa4e14b3d92f39a6e5702f5443103dd1c1092b4cd2b76ea", 70002),
        C("000000a65373067b1d11fa4e14b3d92f39a6e570e2ff31ea43103dd1c1092b4c", 70003),
        D("0000000000000000000000ff213d92f39a6e570e2ea43103dd1c1092b4cd2b76", 70010),
        E("002a65373067b1d11fa4e14b3d92f39a6e570e1a43103dd1c1092b4cd2b76ed5", 70011),
        F("0000000065373067b1d11fa4e14b3d92f39a6e570e1a43103dd1c1092b4cd2ba", 70012),
        G("00004778f504b7e5d796c6449b0a7de391ce958f46e05d7cc4ffd77fa924b8bf", 70013); // this hash contains the the candidateAddress

        private final Hash transactionHash;
        private final int roundIndex;

        MockedCandidate(String transactionHash, int roundIndex) {
            this.transactionHash = HashFactory.TRANSACTION.create(transactionHash);
            this.roundIndex = roundIndex;
        }
    }

    @Before
    public void setup() throws Exception {
        SnapshotMockUtils.mockSnapshotProvider(snapshotProvider);
    }

    @Test
    public void getCandidateNormalizedWeightTest() {
        mockedSeenCandidates.add(MockedCandidate.A.transactionHash); // 0
        mockedSeenCandidates.add(MockedCandidate.B.transactionHash); // 6
        mockedSeenCandidates.add(MockedCandidate.C.transactionHash); // 3
        mockedSeenCandidates.add(MockedCandidate.D.transactionHash); // 11
        mockedSeenCandidates.add(MockedCandidate.E.transactionHash); // 1
        mockedSeenCandidates.add(MockedCandidate.F.transactionHash); // 4
        mockedSeenCandidates.add(MockedCandidate.G.transactionHash); // 2 (candidateAddress)
        /*
        double a = curatorService.getCandidateNormalizedWeight(candidateAddress, mockedSeenCandidates, 0.0);
        double b = curatorService.getCandidateNormalizedWeight(candidateAddress, mockedSeenCandidates, 6.0);
        double c = curatorService.getCandidateNormalizedWeight(candidateAddress, mockedSeenCandidates, 3.0);
        double d = curatorService.getCandidateNormalizedWeight(candidateAddress, mockedSeenCandidates, 11.0);
        double e = curatorService.getCandidateNormalizedWeight(candidateAddress, mockedSeenCandidates, 1.0);
        double f = curatorService.getCandidateNormalizedWeight(candidateAddress, mockedSeenCandidates, 4.0);
        double g = curatorService.getCandidateNormalizedWeight(candidateAddress, mockedSeenCandidates, 2.0);

        Assert.assertEquals("P(a)~0.21%", 0.0021222632400347188, a, 0);
        Assert.assertEquals("P(b)~5.75%",0.057540156921646844, b, 0);
        Assert.assertEquals("P(c)~1.10%",0.011050581878826128, c, 0);
        Assert.assertEquals("P(d)~90.0%",0.9000794932837441, d, 0);
        Assert.assertEquals("P(e)~0.36%",0.003678419165499213, e, 0);
        Assert.assertEquals("P(f)~1.19%",0.019153454390666135, f, 0);
        Assert.assertEquals("P(g)~0.63%",0.0063756311195827765, g, 0);
        */
    }
}

