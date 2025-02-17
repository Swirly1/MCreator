/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.integration.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.mcreator.blockly.IBlockGenerator;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.blockly.data.StatementInput;
import net.mcreator.blockly.data.ToolboxBlock;
import net.mcreator.element.ModElementType;
import net.mcreator.element.types.Procedure;
import net.mcreator.generator.GeneratorStats;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.minecraft.ElementUtil;
import net.mcreator.ui.blockly.BlocklyJavascriptBridge;
import net.mcreator.util.ListUtils;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.apache.logging.log4j.Logger;

import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class GTProcedureBlocks {

	public static void runTest(Logger LOG, String generatorName, Random random, Workspace workspace) {
		// silently skip if procedures are not supported by this generator
		if (workspace.getGeneratorStats().getModElementTypeCoverageInfo().get(ModElementType.PROCEDURE)
				== GeneratorStats.CoverageStatus.NONE) {
			return;
		}

		Set<String> generatorBlocks = workspace.getGeneratorStats().getGeneratorProcedures();

		for (ToolboxBlock procedureBlock : BlocklyLoader.INSTANCE.getProcedureBlockLoader().getDefinedBlocks()
				.values()) {
			StringBuilder additionalXML = new StringBuilder();

			// silently skip procedure blocks not supported by this generator
			if (!generatorBlocks.contains(procedureBlock.machine_name)) {
				continue;
			}

			if (procedureBlock.toolboxXML == null) {
				LOG.warn("[" + generatorName + "] Skipping procedure block without default XML defined: "
						+ procedureBlock.machine_name);
				continue;
			}

			if (!procedureBlock.getInputs().isEmpty()) {
				boolean templatesDefined = true;

				if (procedureBlock.toolbox_init != null) {
					for (String input : procedureBlock.getInputs()) {
						boolean match = false;
						for (String toolboxtemplate : procedureBlock.toolbox_init) {
							if (toolboxtemplate.contains("<value name=\"" + input + "\">")) {
								match = true;
								break;
							}
						}

						if (!match) {
							templatesDefined = false;
							break;
						}
					}
				} else {
					templatesDefined = false;
				}

				if (!templatesDefined) {
					LOG.warn("[" + generatorName + "] Skipping procedure block with incomplete template: "
							+ procedureBlock.machine_name);
					continue;
				}
			}

			if (procedureBlock.getRequiredAPIs() != null) {
				boolean skip = false;

				for (String required_api : procedureBlock.getRequiredAPIs()) {
					if (!workspace.getWorkspaceSettings().getMCreatorDependencies().contains(required_api)) {
						skip = true;
						break;
					}
				}

				if (skip) {
					// We skip API specific blocks without any warnings logged as we do not intend to test them anyway
					continue;
				}
			}

			if (procedureBlock.getFields() != null) {
				int processed = 0;

				for (String field : procedureBlock.getFields()) {
					try {
						JsonArray args0 = procedureBlock.blocklyJSON.getAsJsonObject().get("args0").getAsJsonArray();
						for (int i = 0; i < args0.size(); i++) {
							JsonObject arg = args0.get(i).getAsJsonObject();
							if (arg.get("name").getAsString().equals(field)) {
								switch (arg.get("type").getAsString()) {
								case "field_checkbox":
									additionalXML.append("<field name=\"").append(field).append("\">TRUE</field>");
									processed++;
									break;
								case "field_number":
									additionalXML.append("<field name=\"").append(field).append("\">1.23d</field>");
									processed++;
									break;
								case "field_input":
									additionalXML.append("<field name=\"").append(field).append("\">test</field>");
									processed++;
									break;
								case "field_dropdown":
									JsonArray opts = arg.get("options").getAsJsonArray();
									JsonArray opt = opts.get((int) (Math.random() * opts.size())).getAsJsonArray();
									additionalXML.append("<field name=\"").append(field).append("\">")
											.append(opt.get(1).getAsString()).append("</field>");
									processed++;
									break;
								}
								break;
							}
						}
					} catch (Exception ignored) {
					}
				}

				if (procedureBlock.blocklyJSON.getAsJsonObject().get("extensions") != null) {
					JsonArray extensions = procedureBlock.blocklyJSON.getAsJsonObject().get("extensions")
							.getAsJsonArray();
					for (int i = 0; i < extensions.size(); i++) {
						String extension = extensions.get(i).getAsString();
						String suggestedFieldName = extension.replace("_list_provider", "");
						String suggestedDataListName = suggestedFieldName;

						// convert to proper field names in some extension cases
						switch (extension) {
						case "gui_list_provider":
							suggestedFieldName = "guiname";
							suggestedDataListName = "gui";
							break;
						case "dimension_custom_list_provider":
							suggestedFieldName = "dimension";
							suggestedDataListName = "dimension_custom";
							break;
						case "biome_dictionary_list_provider":
							suggestedFieldName = "biomedict";
							suggestedDataListName = "biomedictionary";
							break;
						}

						if (procedureBlock.machine_name.contains("potion") && suggestedFieldName.equals("effect"))
							suggestedFieldName = "potion";

						if (suggestedDataListName.equals("biomedictionary"))
							suggestedDataListName = "biomedictionarytypes";

						if (suggestedDataListName.equals("sound_category")) {
							suggestedDataListName = "soundcategories";
							suggestedFieldName = "soundcategory";
						}

						if (suggestedDataListName.equals("plant_type")) {
							suggestedDataListName = "planttype";
							suggestedFieldName = "planttype";
						}

						if (procedureBlock.getFields().contains(suggestedFieldName)) {
							String[] values = BlocklyJavascriptBridge.getListOfForWorkspace(workspace,
									suggestedDataListName);

							if (values.length == 0 || values[0].equals(""))
								values = BlocklyJavascriptBridge.getListOfForWorkspace(workspace,
										suggestedDataListName + "s");

							if (values.length > 0 && !values[0].equals("")) {
								if (suggestedFieldName.equals("entity")) {
									additionalXML.append("<field name=\"entity\">EntityZombie</field>");
								} else {
									additionalXML.append("<field name=\"").append(suggestedFieldName).append("\">")
											.append(ListUtils.getRandomItem(random, values)).append("</field>");
								}
								processed++;
							} else {
								System.err.println("list: " + suggestedDataListName); // todo: remove me
							}
						}
					}
				}

				if (processed != procedureBlock.getFields().size()) {
					LOG.warn("[" + generatorName + "] Skipping procedure block with special fields: "
							+ procedureBlock.machine_name);
					continue;
				}
			}

			if (procedureBlock.getStatements() != null) {
				for (StatementInput statement : procedureBlock.getStatements()) {
					additionalXML.append("<statement name=\"").append(statement.name).append("\">")
							.append("<block type=\"text_print\"><value name=\"TEXT\"><block type=\"math_number\">"
									+ "<field name=\"NUM\">123.456</field></block></value></block>")
							.append("</statement>\n");
				}
			}

			ModElement modElement = new ModElement(workspace, "TestBlock" + procedureBlock.machine_name,
					ModElementType.PROCEDURE);

			String testXML = procedureBlock.toolboxXML;

			// replace common math blocks with blocks that contain double variable to verify things like type casting
			testXML = testXML.replace("<block type=\"coord_x\"></block>",
					"<block type=\"variables_get_number\"><field name=\"VAR\">local:test</field></block>");
			testXML = testXML.replace("<block type=\"coord_y\"></block>",
					"<block type=\"variables_get_number\"><field name=\"VAR\">local:test</field></block>");
			testXML = testXML.replace("<block type=\"coord_z\"></block>",
					"<block type=\"variables_get_number\"><field name=\"VAR\">local:test</field></block>");
			testXML = testXML.replaceAll("<block type=\"math_number\"><field name=\"NUM\">(.*?)</field></block>",
					"<block type=\"variables_get_number\"><field name=\"VAR\">local:test</field></block>");

			// replace common logic blocks with blocks that contain logic variable
			testXML = testXML.replace("<block type=\"logic_boolean\"><field name=\"BOOL\">TRUE</field></block>",
					"<block type=\"variables_get_logic\"><field name=\"VAR\">local:flag</field></block>");
			testXML = testXML.replace("<block type=\"logic_boolean\"><field name=\"BOOL\">FALSE</field></block>",
					"<block type=\"variables_get_logic\"><field name=\"VAR\">local:flag</field></block>");

			// replace common itemstack blocks with blocks that contain logic variable
			testXML = testXML.replace("<block type=\"itemstack_to_mcitem\"></block>",
					"<block type=\"variables_get_itemstack\"><field name=\"VAR\">local:stackvar</field></block>");
			testXML = testXML.replace("<block type=\"mcitem_all\"><field name=\"value\"></field></block>",
					"<block type=\"variables_get_itemstack\"><field name=\"VAR\">local:stackvar</field></block>");

			// set MCItem blocks to some value
			testXML = testXML.replace("<block type=\"mcitem_allblocks\"><field name=\"value\"></field></block>",
					"<block type=\"mcitem_allblocks\"><field name=\"value\">"
							+ TestWorkspaceDataProvider.getRandomMCItem(random,
							ElementUtil.loadBlocks(modElement.getWorkspace())).getName() + "</field></block>");

			testXML = testXML.replace("<block type=\"mcitem_all\"><field name=\"value\"></field></block>",
					"<block type=\"mcitem_all\"><field name=\"value\">" + TestWorkspaceDataProvider.getRandomMCItem(
							random, ElementUtil.loadBlocksAndItems(modElement.getWorkspace())).getName()
							+ "</field></block>");

			// add additional xml to the block definition
			testXML = testXML.replace("<block type=\"" + procedureBlock.machine_name + "\">",
					"<block type=\"" + procedureBlock.machine_name + "\">" + additionalXML);

			Procedure procedure = new Procedure(modElement);

			if (procedureBlock.type == IBlockGenerator.BlockType.PROCEDURAL) {
				procedure.procedurexml = wrapWithBaseTestXML(testXML);
			} else { // output block type
				String rettype = procedureBlock.getOutputType();
				switch (rettype) {
				case "Number":
					procedure.procedurexml = wrapWithBaseTestXML(
							"<block type=\"return_number\"><value name=\"return\">" + testXML + "</value></block>");
					break;
				case "Boolean":
					procedure.procedurexml = wrapWithBaseTestXML(
							"<block type=\"return_logic\"><value name=\"return\">" + testXML + "</value></block>");

					break;
				case "String":
					procedure.procedurexml = wrapWithBaseTestXML(
							"<block type=\"return_text\"><value name=\"return\">" + testXML + "</value></block>");
					break;
				case "MCItem":
					procedure.procedurexml = wrapWithBaseTestXML(
							"<block type=\"return_itemstack\"><value name=\"return\">" + testXML + "</value></block>");
					break;
				default:
					procedure.procedurexml = wrapWithBaseTestXML(
							"<block type=\"text_print\"><value name=\"TEXT\">" + testXML + "</value></block>");
					break;
				}
			}

			try {
				workspace.addModElement(modElement);
				assertTrue(workspace.getGenerator().generateElement(procedure));
				workspace.getModElementManager().storeModElement(procedure);
			} catch (Throwable t) {
				fail("[" + generatorName + "] Failed generating procedure block: " + procedureBlock.machine_name);
				t.printStackTrace();
			}
		}

	}

	public static String wrapWithBaseTestXML(String customXML) {
		return "<xml xmlns=\"https://developers.google.com/blockly/xml\">" + "<variables>"
				+ "<variable type=\"Number\" id=\"test\">test</variable>"
				+ "<variable type=\"Boolean\" id=\"flag\">flag</variable>"
				+ "<variable type=\"MCItem\" id=\"stackvar\">stackvar</variable>" + "</variables>"
				+ "<block type=\"event_trigger\" deletable=\"false\" x=\"59\" y=\"38\">"
				+ "<field name=\"trigger\">no_ext_trigger</field>" + "<next><block type=\"variables_set_logic\">"
				+ "<field name=\"VAR\">local:flag</field><value name=\"VAL\"><block type=\"logic_negate\">"
				+ "<value name=\"BOOL\"><block type=\"variables_get_logic\"><field name=\"VAR\">local:flag</field>"
				+ "</block></value></block></value><next><block type=\"variables_set_number\">"
				+ "<field name=\"VAR\">local:test</field><value name=\"VAL\"><block type=\"math_dual_ops\">"
				+ "<field name=\"OP\">ADD</field><value name=\"A\"><block type=\"variables_get_number\">"
				+ "<field name=\"VAR\">local:test</field></block></value><value name=\"B\"><block type=\"math_number\">"
				+ "<field name=\"NUM\">1.23</field></block></value></block></value><next><block type=\"variables_set_itemstack\">"
				+ "<field name=\"VAR\">local:stackvar</field><value name=\"VAL\"><block type=\"mcitem_all\"><field name=\"value\">"
				+ "Blocks.STONE</field></block></value><next>" + customXML
				+ "</next></block></next></block></next></block></next></block></xml>";
	}

}
