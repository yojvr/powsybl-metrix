/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.metrix.tools;

import com.google.auto.service.AutoService;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import com.powsybl.commons.datasource.DataSource;
import com.powsybl.commons.datasource.DataSourceUtil;
import com.powsybl.iidm.network.ImportConfig;
import com.powsybl.iidm.network.Network;
import com.powsybl.metrix.mapping.*;
import com.powsybl.metrix.mapping.timeseries.CalculatedTimeSeriesStore;
import com.powsybl.metrix.mapping.timeseries.InMemoryTimeSeriesStore;
import com.powsybl.timeseries.ReadOnlyTimeSeriesStore;
import com.powsybl.timeseries.ReadOnlyTimeSeriesStoreAggregator;
import com.powsybl.timeseries.TimeSeriesIndex;
import com.powsybl.timeseries.ast.NodeCalc;
import com.powsybl.tools.Command;
import com.powsybl.tools.Tool;
import com.powsybl.tools.ToolRunningContext;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.codehaus.groovy.runtime.StackTraceUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Paul Bui-Quang {@literal <paul.buiquang at rte-france.com>}
 */
@AutoService(Tool.class)
public class MappingTool implements Tool {

    private static final char SEPARATOR = ';';
    private static final String MAPPING_SYNTHESIS_DIR = "mapping-synthesis-dir";
    private static final String CHECK_EQUIPMENT_TIME_SERIES = "check-equipment-time-series";
    private static final String CHECK_VERSIONS = "check-versions";
    private static final String FIRST_VARIANT = "first-variant";
    private static final String MAX_VARIANT_COUNT = "max-variant-count";

    @Override
    public Command getCommand() {
        return new Command() {
            @Override
            public String getName() {
                return "mapping";
            }

            @Override
            public String getTheme() {
                return "Metrix";
            }

            @Override
            public String getDescription() {
                return "Time serie to network mapping tool";
            }

            @Override
            public Options getOptions() {
                Options options = new Options();
                options.addOption(Option.builder()
                        .longOpt("case-file")
                        .desc("the case to which times series will be mapped")
                        .hasArg()
                        .argName("FILE")
                        .required()
                        .build());
                options.addOption(Option.builder()
                        .longOpt("mapping-file")
                        .desc("Groovy DSL file that describes the mapping")
                        .hasArg()
                        .argName("FILE")
                        .required()
                        .build());
                options.addOption(Option.builder()
                        .longOpt("time-series")
                        .desc("time series csv list")
                        .hasArg()
                        .argName("FILE1,FILE2,...")
                        .required()
                        .build());
                options.addOption(Option.builder()
                        .longOpt(MAPPING_SYNTHESIS_DIR)
                        .desc("output directory to write mapping synthesis files")
                        .hasArg()
                        .argName("DIR")
                        .build());
                options.addOption(Option.builder()
                        .longOpt(CHECK_EQUIPMENT_TIME_SERIES)
                        .desc("check equipment level time series consistency")
                        .build());
                options.addOption(Option.builder()
                        .longOpt(CHECK_VERSIONS)
                        .desc("version list to check, all if option is not set")
                        .hasArg()
                        .argName("VERSION1,VERSION2,...")
                        .build());
                options.addOption(Option.builder()
                        .longOpt("mapping-status-file")
                        .desc("check mapping status of each time series of the DB and write result to the file")
                        .hasArg()
                        .argName("FILE")
                        .build());
                options.addOption(Option.builder()
                        .longOpt("network-output-dir")
                        .desc("output directory to write IIDM networks")
                        .hasArg()
                        .argName("DIR")
                        .build());
                options.addOption(Option.builder()
                        .longOpt("equipment-time-series-dir")
                        .desc("output directory to store equipment level time series")
                        .hasArg()
                        .argName("DIR")
                        .build());
                options.addOption(Option.builder()
                        .longOpt(FIRST_VARIANT)
                        .desc("first variant to simulate")
                        .hasArg()
                        .argName("COUNT")
                        .build());
                options.addOption(Option.builder()
                        .longOpt(MAX_VARIANT_COUNT)
                        .desc("maximum number of variants simulated")
                        .hasArg()
                        .argName("COUNT")
                        .build());
                options.addOption(Option.builder()
                        .longOpt("ignore-limits")
                        .desc("ignore generator limits")
                        .build());
                options.addOption(Option.builder()
                        .longOpt("ignore-empty-filter")
                        .desc("ignore empty filter with non zero time series value")
                        .build());
                return options;
            }

            @Override
            public String getUsageFooter() {
                return null;
            }
        };
    }

    private Path getDir(CommandLine line, ToolRunningContext context, String optionName) throws IOException {
        Path dir = getFile(line, context, optionName);
        if (dir != null) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    private Path getFile(CommandLine line, ToolRunningContext context, String optionName) {
        Path file = null;
        if (line.hasOption(optionName)) {
            file = context.getFileSystem().getPath(line.getOptionValue(optionName));
        }
        return file;
    }

    private Writer getWriter(Path filePath) {
        if (filePath != null) {
            try {
                return Files.newBufferedWriter(filePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return null;
    }

    private ReadOnlyTimeSeriesStoreAggregator getStoreAggregator(Map<String, NodeCalc> nodes, ReadOnlyTimeSeriesStore store) {
        List<ReadOnlyTimeSeriesStore> stores = new ArrayList<>(2);
        stores.add(new CalculatedTimeSeriesStore(nodes, store));
        stores.add(store);
        return new ReadOnlyTimeSeriesStoreAggregator(stores);
    }

    @Override
    public void run(CommandLine line, ToolRunningContext context) {
        try {
            Path caseFile = context.getFileSystem().getPath(line.getOptionValue("case-file"));
            Path mappingFile = context.getFileSystem().getPath(line.getOptionValue("mapping-file"));
            List<String> tsCsvs = Arrays.stream(line.getOptionValue("time-series").split(",")).map(String::valueOf).toList();
            if (tsCsvs.isEmpty()) {
                throw new IllegalArgumentException("Space list is empty");
            }
            Path mappingSynthesisDir = getDir(line, context, MAPPING_SYNTHESIS_DIR);
            Path mappingStatusFile = getFile(line, context, "mapping-status-file");
            boolean checkEquipmentTimeSeries = line.hasOption(CHECK_EQUIPMENT_TIME_SERIES);
            TreeSet<Integer> versions = null;
            if (line.hasOption(CHECK_VERSIONS)) {
                versions = Arrays.stream(line.getOptionValue(CHECK_VERSIONS).split(",")).map(Integer::valueOf).collect(Collectors.toCollection(TreeSet::new));
                if (versions.isEmpty()) {
                    throw new IllegalArgumentException("Version list is empty");
                }
            }
            if (checkEquipmentTimeSeries && versions == null) {
                throw new IllegalArgumentException("check-versions has to be set when check-equipment-time-series is set");
            }
            int firstVariant = line.hasOption(FIRST_VARIANT) ? Integer.parseInt(line.getOptionValue(FIRST_VARIANT)) : 0;
            int maxVariantCount = line.hasOption(MAX_VARIANT_COUNT) ? Integer.parseInt(line.getOptionValue(MAX_VARIANT_COUNT)) : Integer.MAX_VALUE;

            InMemoryTimeSeriesStore store = new InMemoryTimeSeriesStore();
            store.importTimeSeries(tsCsvs.stream().map(context.getFileSystem()::getPath).toList());

            context.getOutputStream().println("Loading case...");
            Network network = Network.read(caseFile, context.getShortTimeExecutionComputationManager(), ImportConfig.load(), null);

            context.getOutputStream().println("Mapping time series to case...");
            TimeSeriesMappingConfig config;
            MappingParameters mappingParameters = MappingParameters.load();
            ComputationRange computationRange = new ComputationRange(versions != null ? versions : store.getTimeSeriesDataVersions(), firstVariant, maxVariantCount);
            TimeSeriesMappingLogger logger = new TimeSeriesMappingLogger();
            try (Reader reader = Files.newBufferedReader(mappingFile, StandardCharsets.UTF_8);
                 Writer scriptLogWriter = mappingSynthesisDir != null ? getWriter(mappingSynthesisDir.resolve("script-logs.csv")) : null) {
                TimeSeriesDslLoader dslLoader = new TimeSeriesDslLoader(reader, mappingFile.getFileName().toString());
                Stopwatch stopwatch = Stopwatch.createStarted();
                network.addListener(new NetworkTopographyChangeNotifier("extern tool", logger));
                config = dslLoader.load(network, mappingParameters, store, new DataTableStore(), scriptLogWriter, computationRange);
                context.getOutputStream().println("Mapping done in " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");
            }

            if (!new TimeSeriesMappingConfigChecker(config).isMappingComplete()) {
                context.getErrorStream().println("Mapping is incomplete");
            }

            // Local parameters
            LocalParameters localParameters = new LocalParameters(config, store, network, versions);

            // Mapping Synthesis
            writeMappingSynthesis(context, localParameters, computationRange, mappingParameters, mappingSynthesisDir);

            // Mapping Status file
            writeMappingStatusFile(mappingStatusFile, context, localParameters);

            // Computing equipment time series
            writeEquipementTimeSeries(line, context, localParameters, mappingParameters, logger);

            if (mappingSynthesisDir != null) {
                logger.writeCsv(mappingSynthesisDir.resolve("mapping-logs.csv"));
            }
        } catch (Exception e) {
            Throwable rootCause = StackTraceUtils.sanitizeRootCause(e);
            rootCause.printStackTrace(context.getErrorStream());
        }
    }

    private void writeMappingSynthesis(ToolRunningContext context, LocalParameters localParameters,
                                       ComputationRange computationRange, MappingParameters mappingParameters,
                                       Path mappingSynthesisDir) {

        TimeSeriesMappingConfigSynthesisCsvWriter csvSynthesisWriter = new TimeSeriesMappingConfigSynthesisCsvWriter(localParameters.config());
        csvSynthesisWriter.printMappingSynthesis(context.getOutputStream());

        if (mappingSynthesisDir != null) {
            context.getOutputStream().println("Writing mapping synthesis to " + mappingSynthesisDir + "...");
            csvSynthesisWriter.writeMappingSynthesis(mappingSynthesisDir);

            ReadOnlyTimeSeriesStore storeAggregator = getStoreAggregator(localParameters.config().getTimeSeriesNodes(), localParameters.store());
            TimeSeriesMappingConfigCsvWriter csvWriter = new TimeSeriesMappingConfigCsvWriter(localParameters.config(), localParameters.network(), storeAggregator, computationRange, mappingParameters.getWithTimeSeriesStats());
            csvWriter.writeMappingCsv(mappingSynthesisDir);
            csvSynthesisWriter.writeMappingSynthesisCsv(mappingSynthesisDir);
        }
    }

    private void writeMappingStatusFile(Path mappingStatusFile, ToolRunningContext context,
                                        LocalParameters localParameters) throws IOException {

        if (mappingStatusFile != null) {
            context.getOutputStream().println("Writing time series mapping status to " + mappingStatusFile + "...");
            new TimeSeriesMappingConfigStatusCsvWriter(localParameters.config(), localParameters.store()).writeTimeSeriesMappingStatus(mappingStatusFile);
        }
    }

    private void writeEquipementTimeSeries(CommandLine line, ToolRunningContext context,
                                           LocalParameters localParameters,
                                           MappingParameters mappingParameters,
                                           TimeSeriesMappingLogger logger) throws IOException {

        Path mappingSynthesisDir = getDir(line, context, MAPPING_SYNTHESIS_DIR);
        Path equipmentTimeSeriesDir = getDir(line, context, "equipment-time-series-dir");
        Path networkOutputDir = getDir(line, context, "network-output-dir");
        boolean checkEquipmentTimeSeries = line.hasOption(CHECK_EQUIPMENT_TIME_SERIES);
        int firstVariant = line.hasOption(FIRST_VARIANT) ? Integer.parseInt(line.getOptionValue(FIRST_VARIANT)) : 0;
        int maxVariantCount = line.hasOption(MAX_VARIANT_COUNT) ? Integer.parseInt(line.getOptionValue(MAX_VARIANT_COUNT)) : Integer.MAX_VALUE;
        boolean ignoreLimits = line.hasOption("ignore-limits");
        boolean ignoreEmptyFilter = line.hasOption("ignore-empty-filter");

        // Local parameters

        if (checkEquipmentTimeSeries) {
            context.getOutputStream().println("Computing equipment time series...");
            TimeSeriesIndex index = new TimeSeriesMappingConfigTableLoader(localParameters.config(), localParameters.store()).checkIndexUnicity();

            int lastPoint = Math.min(firstVariant + maxVariantCount, index.getPointCount()) - 1;
            Range<Integer> range = Range.closed(firstVariant, lastPoint);

            BalanceSummary balanceSummary = new BalanceSummary(context.getOutputStream());
            List<TimeSeriesMapperObserver> observers = new ArrayList<>(1);
            observers.add(balanceSummary);
            if (networkOutputDir != null) {
                DataSource dataSource = DataSourceUtil.createDataSource(networkOutputDir, localParameters.network().getId(), null);
                observers.add(new NetworkPointWriter(localParameters.network(), dataSource));
            }
            if (equipmentTimeSeriesDir != null) {
                observers.add(new EquipmentTimeSeriesWriterObserver(localParameters.network(), localParameters.config(), maxVariantCount, range, equipmentTimeSeriesDir));
                observers.add(new EquipmentGroupTimeSeriesWriterObserver(localParameters.network(), localParameters.config(), maxVariantCount, range, equipmentTimeSeriesDir));
            }

            TimeSeriesMapperParameters parameters = new TimeSeriesMapperParameters(localParameters.versions(), range, ignoreLimits,
                ignoreEmptyFilter, false, mappingParameters.getToleranceThreshold());
            TimeSeriesMapper mapper = new TimeSeriesMapper(localParameters.config(), parameters, localParameters.network(), logger);
            mapper.mapToNetwork(localParameters.store(), observers);

            if (mappingSynthesisDir != null) {
                balanceSummary.writeCsv(mappingSynthesisDir, SEPARATOR);
            }
        }
    }

    private record LocalParameters(TimeSeriesMappingConfig config, InMemoryTimeSeriesStore store, Network network,
                                   TreeSet<Integer> versions) {
    }
}
