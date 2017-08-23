package org.broadinstitute.hellbender.tools.spark.sv.evidence;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.annotations.VisibleForTesting;
import htsjdk.samtools.Cigar;
import htsjdk.samtools.TextCigarCodec;
import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.tools.spark.sv.utils.SVInterval;
import org.broadinstitute.hellbender.utils.read.GATKRead;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Various types of read anomalies that provide evidence of genomic breakpoints.
 * There is a shallow hierarchy based on this class that classifies the anomaly as, for example, a split read,
 *   or a pair that has both reads on the same reference strand.
 * Each BreakpointEvidence object of type ReadEvidence object comes from examining a single read, and describing its
 *   funkiness, if any, by instantiating one of the subclasses that addresses that type of funkiness. Instances of
 *   BreakpointEvidence that are not of subtype ReadEvidence consolidate information from one or more pieces of
 *   ReadEvidence into a single object.
 */
@DefaultSerializer(BreakpointEvidence.Serializer.class)
public class BreakpointEvidence {
    private static final SVInterval.Serializer intervalSerializer = new SVInterval.Serializer();
    private final SVInterval location;
    private final int weight;
    private boolean validated; // this piece of evidence is consistent with enough other evidence to be taken seriously

    public BreakpointEvidence( final SVInterval location, final int weight, final boolean validated ) {
        this.location = location;
        this.weight = weight;
        this.validated = validated;
    }

    protected BreakpointEvidence( final Kryo kryo, final Input input ) {
        this.location = intervalSerializer.read(kryo, input, SVInterval.class);
        this.weight = input.readInt();
        this.validated = input.readBoolean();
    }

    public SVInterval getLocation() { return location; }
    public int getWeight() { return weight; }
    public boolean isValidated() { return validated; }
    public void setValidated( final boolean validated ) { this.validated = validated; }

    /**
     * Returns the strand of the evidence, if applicable; otherwise null.
     */
    public Boolean isForwardStrand() { return null; }

    /**
     * Returns true if this piece of evidence specifies a possible distal target for the breakpoint.
     * @param readMetadata
     */
    public boolean hasDistalTargets(final ReadMetadata readMetadata) {
        return false;
    }

    /**
     * Returns the distal interval implicated as a candidate adjacency to the breakpoint by this piece of evidence.
     * For example, in the case of a discordant read pair, this would be the region adjacent to the mate of the current
     * read. Returns null if the evidence does not specify or support a possible targeted region (for example, the case
     * of an read with an unmapped mate).
     */
    public List<SVInterval> getDistalTargets(final ReadMetadata readMetadata) {
        return null;
    }

    /**
     * Returns the strands of the distal target intervals
     * @param readMetadata
     */
    public List<Boolean> getDistalTargetStrands(final ReadMetadata readMetadata) {
        return null;
    }

    @Override
    public String toString() {
        return location.toString() + "^" + weight;
    }

    protected void serialize( final Kryo kryo, final Output output ) {
        intervalSerializer.write(kryo, output, location);
        output.writeInt(weight);
        output.writeBoolean(validated);
    }

    public static final class Serializer extends com.esotericsoftware.kryo.Serializer<BreakpointEvidence> {
        @Override
        public void write( final Kryo kryo, final Output output, final BreakpointEvidence evidence ) {
            evidence.serialize(kryo, output);
        }

        @Override
        public BreakpointEvidence read( final Kryo kryo, final Input input, final Class<BreakpointEvidence> klass ) {
            return new BreakpointEvidence(kryo, input);
        }
    }

    @DefaultSerializer(TemplateSizeAnomaly.Serializer.class)
    public final static class TemplateSizeAnomaly extends BreakpointEvidence {
        private final int readCount;

        public TemplateSizeAnomaly( final SVInterval interval, final int weight, final int readCount ) {
            super(interval, weight, false);
            this.readCount = readCount;
        }

        protected TemplateSizeAnomaly( final Kryo kryo, final Input input ) {
            super(kryo, input);
            readCount = input.readInt();
        }

        @Override
        protected void serialize( final Kryo kryo, final Output output ) {
            super.serialize(kryo, output);
            output.writeInt(readCount);
        }

        @Override
        public String toString() {
            return super.toString() + "\tTemplateSizeAnomaly\t" + readCount;
        }

        public final static class Serializer extends com.esotericsoftware.kryo.Serializer<TemplateSizeAnomaly> {
            @Override
            public void write( final Kryo kryo, final Output output, final TemplateSizeAnomaly templateSizeAnomaly ) {
                templateSizeAnomaly.serialize(kryo, output);
            }

            @Override
            public TemplateSizeAnomaly read( final Kryo kryo, final Input input, final Class<TemplateSizeAnomaly> klass ) {
                return new TemplateSizeAnomaly(kryo, input);
            }
        }
    }

    @DefaultSerializer(ReadEvidence.Serializer.class)
    public static class ReadEvidence extends BreakpointEvidence {
        private static final int SINGLE_READ_WEIGHT = 1;
        private final String templateName; // QNAME of the read that was funky (i.e., the name of the fragment)
        private final TemplateFragmentOrdinal fragmentOrdinal; // which read we're talking about (first or last, for paired-end reads)
        private final boolean forwardStrand;

        /**
         * evidence offset and width is set to "the rest of the fragment" not covered by this read
         */
        protected ReadEvidence( final GATKRead read, final ReadMetadata metadata ) {
            super(restOfFragmentInterval(read,metadata), SINGLE_READ_WEIGHT, false);
            this.templateName = read.getName();
            if ( templateName == null ) throw new GATKException("Read has no name.");
            this.fragmentOrdinal = TemplateFragmentOrdinal.forRead(read);
            this.forwardStrand = ! read.isReverseStrand();
        }

        /**
         * for use when the uncertainty in location has a fixed size
         */
        protected ReadEvidence( final GATKRead read, final ReadMetadata metadata,
                                final int contigOffset, final int offsetUncertainty, final boolean forwardStrand ) {
            super(fixedWidthInterval(metadata.getContigID(read.getContig()),contigOffset,offsetUncertainty),
                    SINGLE_READ_WEIGHT, false);
            this.templateName = read.getName();
            if ( templateName == null ) throw new GATKException("Read has no name.");
            this.fragmentOrdinal = TemplateFragmentOrdinal.forRead(read);
            this.forwardStrand = forwardStrand;
        }

        @VisibleForTesting ReadEvidence( final SVInterval interval, final int weight,
                                         final String templateName, final TemplateFragmentOrdinal fragmentOrdinal,
                                         final boolean validated, final boolean forwardStrand ) {
            super(interval, weight, validated);
            this.templateName = templateName;
            this.fragmentOrdinal = fragmentOrdinal;
            this.forwardStrand = forwardStrand;
        }

        /**
         * a technical constructor for use in Kryo (de-)serialization.
         * this creates an object by reading a Kryo-serialized stream.
         * it will be called by subclasses in their own constructors from Kryo streams (as super(kryo, input)).
         */
        protected ReadEvidence( final Kryo kryo, final Input input ) {
            super(kryo, input);
            this.templateName = input.readString();
            this.fragmentOrdinal = TemplateFragmentOrdinal.values()[input.readByte()];
            this.forwardStrand = input.readBoolean();
        }

        @Override
        protected void serialize( final Kryo kryo, final Output output ) {
            super.serialize(kryo, output);
            output.writeString(templateName);
            output.writeByte(fragmentOrdinal.ordinal());
            output.writeBoolean(forwardStrand);
        }

        public String getTemplateName() {
            return templateName;
        }

        public TemplateFragmentOrdinal getFragmentOrdinal() {
            return fragmentOrdinal;
        }

        @Override
        public Boolean isForwardStrand() {
            return forwardStrand;
        }

        @Override
        public String toString() {
            return super.toString() + "\t" + templateName + fragmentOrdinal;
        }

        public static final class Serializer extends com.esotericsoftware.kryo.Serializer<ReadEvidence> {
            @Override
            public void write( final Kryo kryo, final Output output, final ReadEvidence evidence ) {
                evidence.serialize(kryo, output);
            }

            @Override
            public ReadEvidence read( final Kryo kryo, final Input input, final Class<ReadEvidence> klass ) {
                return new ReadEvidence(kryo, input);
            }
        }

        private static SVInterval restOfFragmentInterval( final GATKRead read, final ReadMetadata metadata ) {
            final int templateLen = metadata.getFragmentLengthStatistics(read.getReadGroup()).getMaxNonOutlierFragmentSize();
            int width;
            int start;
            if ( read.isReverseStrand() ) {
                // we can get a little more precise about the interval by checking to see if there are any leading mismatches
                // in the read's alignment and trimming them off.
                int leadingMismatches = getLeadingMismatches(read, true);
                final int readStart = read.getStart() + leadingMismatches;
                width = readStart - (read.getUnclippedEnd() + 1 - templateLen);
                start = readStart - width;
                if ( start < 1 ) {
                    width += start - 1;
                    start = 1;
                }
            } else {
                int trailingMismatches = getLeadingMismatches(read, false);
                final int readEnd = read.getEnd() + 1 - trailingMismatches;
                width = read.getUnclippedStart() + templateLen - readEnd;
                start = readEnd;
            }
            return new SVInterval(metadata.getContigID(read.getContig()), start, start + width);
        }

        @VisibleForTesting static int getLeadingMismatches(final GATKRead read, final boolean fromStart) {
            int leadingMismatches = 0;
            if (read.hasAttribute("MD")) {
                final String mdString = read.getAttributeAsString("MD");
                final List<TextMDCodec.MDElement> mdElements = TextMDCodec.parseMDString(mdString);
                int idx = fromStart ? 0 : (mdElements.size() - 1);
                while (fromStart ? (idx < mdElements.size()) : (idx >= 0)) {
                    TextMDCodec.MDElement mdElement = mdElements.get(idx);
                    if (mdElement instanceof TextMDCodec.MatchMDElement && mdElement.getLength() > 0) {
                        break;
                    } else {
                        leadingMismatches += mdElement.getLength();
                    }
                    idx = idx + (fromStart ? 1 : -1);
                }
            }
            return leadingMismatches;
        }

        private static SVInterval fixedWidthInterval( final int contigID,
                                                      final int contigOffset, final int offsetUncertainty ) {
            int width = 2 * offsetUncertainty;
            int start = contigOffset - offsetUncertainty;
            if ( start < 1 ) {
                width += start - 1;
                start = 1;
            }
            return new SVInterval(contigID, start, start + width);
        }
    }

    @DefaultSerializer(SplitRead.Serializer.class)
    public static final class SplitRead extends ReadEvidence {
        private static final int UNCERTAINTY = 3;
        private static final String SA_TAG_NAME = "SA";
        private final String cigar;
        private final String tagSA;
        private final List<SAMapping> saMappings;

        public SplitRead( final GATKRead read, final ReadMetadata metadata, final boolean atStart ) {
            // todo: if reads have multiple SA tags.. we should have two pieces of evidence with the right strands
            super(read, metadata, atStart ? read.getStart() : read.getEnd(), UNCERTAINTY, !atStart);
            cigar = read.getCigar().toString();
            if ( cigar.isEmpty() ) throw new GATKException("Read has no cigar string.");
            if (read.hasAttribute(SA_TAG_NAME)) {
                tagSA = read.getAttributeAsString(SA_TAG_NAME);
            } else {
                tagSA = null;
            }
            saMappings = parseSATag(tagSA);
        }

        private SplitRead( final Kryo kryo, final Input input ) {
            super(kryo, input);
            cigar = input.readString();
            tagSA = input.readString();
            saMappings = parseSATag(tagSA);
        }

        @Override
        protected void serialize( final Kryo kryo, final Output output ) {
            super.serialize(kryo, output);
            output.writeString(cigar);
            output.writeString(tagSA);
        }

        @Override
        public String toString() {
            return super.toString()+"\tSplit\t"+cigar+"\t"+(tagSA == null ? " SA: None" : (" SA: " + tagSA));
        }

        private List<SAMapping> parseSATag(final String tagSA) {
            if (tagSA == null) {
                return null;
            } else {
                final String[] saStrings = tagSA.split(";");
                final List<SAMapping> supplementaryAlignments = new ArrayList<>(saStrings.length);
                for (final String saString : saStrings) {
                    final String[] saFields = saString.split(",", -1);
                    if (saFields.length != 6) {
                        throw new GATKException("Could not parse SATag: "+ saString);
                    }
                    final String contigId = saFields[0];
                    final int pos = Integer.parseInt(saFields[1]);
                    final boolean strand = "+".equals(saFields[2]);
                    final String cigarString = saFields[3];
                    final int mapQ = Integer.parseInt(saFields[4]);
                    final int mismatches = Integer.parseInt(saFields[5]);
                    SAMapping saMapping = new SAMapping(contigId, pos, strand, cigarString, mapQ, mismatches);

                    supplementaryAlignments.add(saMapping);
                }
                return supplementaryAlignments;

            }

        }

        @Override
        public boolean hasDistalTargets(final ReadMetadata readMetadata) {
            return tagSA != null && hasHighQualityMappings(readMetadata);
        }

        private boolean hasHighQualityMappings(final ReadMetadata readMetadata) {
            boolean hqMappingFound = false;
            if (saMappings != null) {
                for (final SAMapping mapping : saMappings) {
                    final int mapQ = mapping.getMapq();
                    SVInterval saInterval = saMappingToSVInterval(readMetadata, mapping);
                    if (isHighQualityMapping(readMetadata, mapQ, saInterval)) {
                        hqMappingFound = true;
                        break;
                    }
                }
            }
            return hqMappingFound;
        }

        private boolean isHighQualityMapping(final ReadMetadata readMetadata, final int mapQ, final SVInterval saInterval) {
            return mapQ >= readMetadata.getSvReadFilter().getMinEvidenceMapQ() &&
                    (saInterval.getContig() == getLocation().getContig() ||
                    (!readMetadata.ignoreCrossContigID(saInterval.getContig()) && !readMetadata.ignoreCrossContigID(getLocation().getContig())))
                    && ! saInterval.overlaps(getLocation());
        }

        @Override
        public List<SVInterval> getDistalTargets(final ReadMetadata readMetadata) {
            if (saMappings != null) {
                List<SVInterval> supplementaryAlignments = new ArrayList<>(saMappings.size());
                for (final SAMapping saMapping : saMappings) {
                    final int mapQ = saMapping.getMapq();
                    SVInterval saInterval = saMappingToSVInterval(readMetadata, saMapping);
                    if (! isHighQualityMapping(readMetadata, mapQ, saInterval)) {
                        continue;
                    }
                    supplementaryAlignments.add(saInterval);
                }
                return supplementaryAlignments;
            } else {
                return null;
            }
        }

        @Override
        public List<Boolean> getDistalTargetStrands(final ReadMetadata readMetadata) {
            if (saMappings != null) {
                final List<Boolean> supplementaryAlignmentStrands = new ArrayList<>(saMappings.size());
                for (final SAMapping saMapping : saMappings) {
                    final int mapQ = saMapping.getMapq();
                    SVInterval saInterval = saMappingToSVInterval(readMetadata, saMapping);
                    if (! isHighQualityMapping(readMetadata, mapQ, saInterval)) {
                        continue;
                    }
                    final boolean result = calculateDistalTargetStrand(saMapping, isForwardStrand());
                    supplementaryAlignmentStrands.add(result);
                }
                return supplementaryAlignmentStrands;
            } else {
                return null;
            }
        }

        @VisibleForTesting
        static boolean calculateDistalTargetStrand(final SAMapping saMapping, final boolean primaryEvidenceRightClipped) {
            final Cigar cigar = TextCigarCodec.decode(saMapping.getCigar());
            if (primaryEvidenceRightClipped) {
                return !cigar.isLeftClipped();
            } else {
                return cigar.isRightClipped();
            }
        }

        // todo: for now, taking the entire location of the supplementary alignment plus the uncertainty on each end
        // A better solution might be to find the location of the actual clip on the other end of the reference,
        // but that would be significantly more complex and possibly computationally expensive
        private SVInterval saMappingToSVInterval(final ReadMetadata readMetadata, final SAMapping saMapping) {
            final int contigId = readMetadata.getContigID(saMapping.getContigName());
            final int pos = saMapping.getStart();
            final Cigar cigar = TextCigarCodec.decode(saMapping.getCigar());

            return new SVInterval( contigId,
                    Math.max(1, pos - UNCERTAINTY),
                    pos + cigar.getPaddedReferenceLength() + UNCERTAINTY + 1);

        }

        public static final class Serializer extends com.esotericsoftware.kryo.Serializer<SplitRead> {
            @Override
            public void write( final Kryo kryo, final Output output, final SplitRead splitRead ) {
                splitRead.serialize(kryo, output);
            }

            @Override
            public SplitRead read( final Kryo kryo, final Input input, final Class<SplitRead> klass ) {
                return new SplitRead(kryo, input);
            }
        }

        final static class SAMapping {
            private final String contigName;
            private final int start;
            private final boolean forwardStrand;
            private final String cigar;
            private final int mapq;
            private final int mismatches;

            public SAMapping(final String contigName, final int start, final boolean strand, final String cigar, final int mapq, final int mismatches) {
                this.contigName = contigName;
                this.start = start;
                this.forwardStrand = strand;
                this.cigar = cigar;
                this.mapq = mapq;
                this.mismatches = mismatches;
            }

            public String getContigName() {
                return contigName;
            }

            public int getStart() {
                return start;
            }

            public boolean isForwardStrand() {
                return forwardStrand;
            }

            public String getCigar() {
                return cigar;
            }

            public int getMapq() {
                return mapq;
            }

            public int getMismatches() {
                return mismatches;
            }
        }
    }

    @DefaultSerializer(LargeIndel.Serializer.class)
    public static final class LargeIndel extends ReadEvidence {
        private static final int UNCERTAINTY = 4;
        private final String cigar;

        LargeIndel( final GATKRead read, final ReadMetadata metadata, final int contigOffset ) {
            super(read, metadata, contigOffset, UNCERTAINTY, true);
            cigar = read.getCigar().toString();
            if ( cigar == null ) throw new GATKException("Read has no cigar string.");
        }

        private LargeIndel( final Kryo kryo, final Input input ) {
            super(kryo, input);
            cigar = input.readString();
        }

        @Override
        protected void serialize( final Kryo kryo, final Output output ) {
            super.serialize(kryo, output);
            output.writeString(cigar);
        }

        @Override
        public String toString() {
            return super.toString() + "\tIndel\t" + cigar;
        }

        public static final class Serializer extends com.esotericsoftware.kryo.Serializer<LargeIndel> {
            @Override
            public void write( final Kryo kryo, final Output output, final LargeIndel largeIndel ) {
                largeIndel.serialize(kryo, output);
            }

            @Override
            public LargeIndel read( final Kryo kryo, final Input input, final Class<LargeIndel> klass ) {
                return new LargeIndel(kryo, input);
            }
        }
    }

    @DefaultSerializer(MateUnmapped.Serializer.class)
    public static final class MateUnmapped extends ReadEvidence {

        MateUnmapped( final GATKRead read, final ReadMetadata metadata ) {
            super(read, metadata);
        }

        private MateUnmapped( final Kryo kryo, final Input input ) { super(kryo, input); }

        @Override
        public String toString() {
            return super.toString() + "\tUnmappedMate";
        }

        public static final class Serializer extends com.esotericsoftware.kryo.Serializer<MateUnmapped> {
            @Override
            public void write( final Kryo kryo, final Output output, final MateUnmapped mateUnmapped ) {
                mateUnmapped.serialize(kryo, output);
            }

            @Override
            public MateUnmapped read( final Kryo kryo, final Input input, final Class<MateUnmapped> klass ) {
                return new MateUnmapped(kryo, input);
            }
        }
    }

    public static abstract class DiscordantReadPairEvidence extends ReadEvidence {
        protected final SVInterval target;
        protected final boolean targetForwardStrand;
        protected final int targetQuality;

        // even if we have access to and use the mate cigar, we still don't really know the exact breakpoint interval
        // specified by the mate since there could be unclipped mismatches at the ends of the alignment. This constant
        // tries to correct for that.
        public static final int MATE_ALIGNMENT_LENGTH_UNCERTAINTY = 2;

        public DiscordantReadPairEvidence(final GATKRead read, final ReadMetadata metadata) {
            super(read, metadata);
            target = getMateTargetInterval(read, metadata);
            targetForwardStrand = getMateForwardStrand(read);
            if (read.hasAttribute("MQ")) {
                targetQuality = read.getAttributeAsInteger("MQ");
            } else {
                targetQuality = Integer.MAX_VALUE;
            }
        }

        public DiscordantReadPairEvidence(final Kryo kryo, final Input input) {
            super(kryo, input);
            target = intervalSerializer.read(kryo, input, SVInterval.class);
            targetForwardStrand = input.readBoolean();
            targetQuality = input.readInt();
        }

        @Override
        protected void serialize(final Kryo kryo, final Output output) {
            super.serialize(kryo, output);
            intervalSerializer.write(kryo, output, target);
            output.writeBoolean(targetForwardStrand);
            output.writeInt(targetQuality);
        }

        @Override
        public boolean hasDistalTargets(final ReadMetadata readMetadata) {
            return isTargetHighQuality(readMetadata);
        }

        private boolean isTargetHighQuality(final ReadMetadata readMetadata) {
            return targetQuality >= readMetadata.getSvReadFilter().getMinEvidenceMapQ()
                    && ! target.overlaps(getLocation()) && ! readMetadata.ignoreCrossContigID(target.getContig());
        }

        @Override
        public List<SVInterval> getDistalTargets(final ReadMetadata readMetadata) {
            if (isTargetHighQuality(readMetadata)) {
                return Collections.singletonList(target);
            } else {
                return Collections.emptyList();
            }
        }

        @Override
        public List<Boolean> getDistalTargetStrands(final ReadMetadata readMetadata) {
            if (isTargetHighQuality(readMetadata)) {
                return Collections.singletonList(targetForwardStrand);
            } else {
                return Collections.emptyList();
            }
        }

        /**
         * Finds the coordinates implicated by the read's mate as being part of the breakpoint, ie. the coordinates
         * to the 3' end of the mate, where the breakpoint might lie. Given that we don't have the actual mate read here,
         * we make two small assumptions: first, that the length of the mate is equal to the length of the read we are looking at.
         * Second, since we don't have the mate's CIGAR we can't actually compute the end coordinates of the mate alignment,
         * which might be pushed away from where we think it is by a large indel.
         */
        protected SVInterval getMateTargetInterval(final GATKRead read, final ReadMetadata metadata) {
            final int mateContigIndex = metadata.getContigID(read.getMateContig());
            final int mateStartPosition = read.getMateStart();
            final boolean mateReverseStrand = read.mateIsReverseStrand();

            final int maxAllowableFragmentSize = metadata.getFragmentLengthStatistics(read.getReadGroup()).getMaxNonOutlierFragmentSize();

            final int mateAlignmentLength;
            // if the read has an MC attribute we don't have to assume the aligned read length of the mate
            if (read.hasAttribute("MC")) {
                mateAlignmentLength = TextCigarCodec.decode(read.getAttributeAsString("MC")).getPaddedReferenceLength();
            } else {
                mateAlignmentLength = read.getLength();
            }
            return new SVInterval(mateContigIndex,
                    Math.max(0, mateReverseStrand ?
                                    mateStartPosition - maxAllowableFragmentSize + mateAlignmentLength :
                                    mateStartPosition + mateAlignmentLength - MATE_ALIGNMENT_LENGTH_UNCERTAINTY),
                    mateReverseStrand ?
                            mateStartPosition + MATE_ALIGNMENT_LENGTH_UNCERTAINTY :
                            mateStartPosition + maxAllowableFragmentSize);
        }

        protected boolean getMateForwardStrand(final GATKRead read) {
            return ! read.mateIsReverseStrand();
        }

    }

    @DefaultSerializer(InterContigPair.Serializer.class)
    public static final class InterContigPair extends DiscordantReadPairEvidence {

        InterContigPair( final GATKRead read, final ReadMetadata metadata ) {
            super(read, metadata);
        }

        private InterContigPair( final Kryo kryo, final Input input ) {
            super(kryo, input);
        }

        @Override
        public String toString() {
            return super.toString() + "\tIntercontigPair\t" + target;
        }

        public static final class Serializer extends com.esotericsoftware.kryo.Serializer<InterContigPair> {
            @Override
            public void write( final Kryo kryo, final Output output, final InterContigPair interContigPair ) {
                interContigPair.serialize(kryo, output);
            }

            @Override
            public InterContigPair read( final Kryo kryo, final Input input, final Class<InterContigPair> klass ) {
                return new InterContigPair(kryo, input);
            }
        }
    }

    @DefaultSerializer(OutiesPair.Serializer.class)
    public static final class OutiesPair extends DiscordantReadPairEvidence {

        OutiesPair( final GATKRead read, final ReadMetadata metadata ) {
            super(read, metadata);
        }

        private OutiesPair( final Kryo kryo, final Input input ) {
            super(kryo, input);
        }


        @Override
        protected void serialize( final Kryo kryo, final Output output ) {
            super.serialize(kryo, output);
        }

        @Override
        public String toString() {
            return super.toString() + "\tOutiesPair\t" + target;
        }

        public static final class Serializer extends com.esotericsoftware.kryo.Serializer<OutiesPair> {
            @Override
            public void write( final Kryo kryo, final Output output, final OutiesPair mateUnmapped ) {
                mateUnmapped.serialize(kryo, output);
            }

            @Override
            public OutiesPair read( final Kryo kryo, final Input input, final Class<OutiesPair> klass ) {
                return new OutiesPair(kryo, input);
            }
        }
    }

    @DefaultSerializer(SameStrandPair.Serializer.class)
    public static final class SameStrandPair extends DiscordantReadPairEvidence {

        SameStrandPair( final GATKRead read, final ReadMetadata metadata ) {
            super(read, metadata);
        }

        private SameStrandPair( final Kryo kryo, final Input input ) {
            super(kryo, input);
        }

        @Override
        public String toString() {
            return super.toString() + "\tSameStrandPair\t" + target;
        }

        public static final class Serializer extends com.esotericsoftware.kryo.Serializer<SameStrandPair> {
            @Override
            public void write( final Kryo kryo, final Output output, final SameStrandPair sameStrandPair ) {
                sameStrandPair.serialize(kryo, output);
            }

            @Override
            public SameStrandPair read( final Kryo kryo, final Input input, final Class<SameStrandPair> klass ) {
                return new SameStrandPair(kryo, input);
            }
        }
    }

    @DefaultSerializer(WeirdTemplateSize.Serializer.class)
    public static final class WeirdTemplateSize extends DiscordantReadPairEvidence {
        private final int templateSize;
        private final int mateStartPosition;
        private final boolean mateReverseStrand;

        WeirdTemplateSize( final GATKRead read, final ReadMetadata metadata ) {
            super(read, metadata);
            this.templateSize = read.getFragmentLength();
            this.mateStartPosition = read.getMateStart();
            this.mateReverseStrand = read.mateIsReverseStrand();
        }

        private WeirdTemplateSize( final Kryo kryo, final Input input ) {
            super(kryo, input);
            templateSize = input.readInt();
            mateStartPosition = input.readInt();
            mateReverseStrand = input.readBoolean();
        }

        @Override
        protected void serialize( final Kryo kryo, final Output output ) {
            super.serialize(kryo, output);
            output.writeInt(templateSize);
            output.writeInt(mateStartPosition);
            output.writeBoolean(mateReverseStrand);
        }

        @Override
        public String toString() {
            return super.toString() + "\tTemplateSize\t" + target + "\t" + templateSize;
        }

        public static final class Serializer extends com.esotericsoftware.kryo.Serializer<WeirdTemplateSize> {
            @Override
            public void write( final Kryo kryo, final Output output, final WeirdTemplateSize weirdTemplateSize ) {
                weirdTemplateSize.serialize(kryo, output);
            }

            @Override
            public WeirdTemplateSize read( final Kryo kryo, final Input input, final Class<WeirdTemplateSize> klass ) {
                return new WeirdTemplateSize(kryo, input);
            }
        }
    }
}