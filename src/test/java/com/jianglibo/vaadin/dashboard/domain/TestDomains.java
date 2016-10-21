package com.jianglibo.vaadin.dashboard.domain;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;
import com.jianglibo.vaadin.dashboard.Tbase;
import com.jianglibo.vaadin.dashboard.repositories.BoxGroupRepository;
import com.jianglibo.vaadin.dashboard.repositories.BoxRepository;
import com.jianglibo.vaadin.dashboard.security.PersonVo;
import com.jianglibo.vaadin.dashboard.service.AppObjectMappers;
import com.jianglibo.vaadin.dashboard.sshrunner.EnvForCodeExec;
import com.jianglibo.vaadin.dashboard.taskrunner.OneThreadTaskDesc;
import com.jianglibo.vaadin.dashboard.taskrunner.TaskDesc;
import com.jianglibo.vaadin.dashboard.util.SoftwareFolder;
import com.jianglibo.vaadin.dashboard.vo.ConfigContent;

/**
 * Create boxGroup and boxes from fixtures/domain/boxgroup.yaml, Create all
 * software EnvForCodeExec.
 * 
 * @author jianglibo@gmail.com
 *
 */
public class TestDomains extends Tbase {

	@Autowired
	private BoxGroupRepository boxGroupRepository;

	@Autowired
	private BoxRepository boxRepository;

	@Autowired
	private AppObjectMappers appObjectMappers;

	private Path softwarePath = Paths.get("softwares");

	private String tbasename = "envforcodeexec.";

	@After
	public void after() {
		List<BoxGroup> bgs = boxGroupRepository.findAll();
		bgs.forEach(bg -> {
			bg.getBoxes().stream().forEach(b -> {
				b.getBoxGroups().remove(bg);
				boxRepository.save(b);
			});
			bg.setBoxes(Sets.newHashSet());
			boxGroupRepository.delete(bg);
		});

		boxRepository.deleteAll();
		softwareRepository.deleteAll();
	}
	
	private void writeOneFixture(Person root, BoxGroup onebg, Software sf, SoftwareFolder sfolder) throws IOException {
		Software sfInDb = softwareRepository.findByNameAndOstypeAndSversion(sf.getName(), sf.getOstype(),
				sf.getSversion());
		if (sfInDb != null) {
			softwareRepository.delete(sfInDb);
		}
		sf.setCreator(root);
		softwareRepository.save(sf);
		
		TaskDesc td = new TaskDesc("", new PersonVo.PersonVoBuilder(getFirstPerson()).build(), onebg,
				Sets.newHashSet(), sf, "install");

		OneThreadTaskDesc ottd = td.createOneThreadTaskDescs().get(0);

		EnvForCodeExec efce = new EnvForCodeExec.EnvForCodeExecBuilder(appObjectMappers, ottd,
				"/opt/easyinstaller").build();

		assertThat("should have 3 boxes", efce.getBoxGroup().getBoxes().size(), equalTo(3));
		assertThat("first box should be 10", efce.getBoxGroup().getBoxes().get(0).getIp(),
				equalTo("192.168.2.10"));
		assertThat("first box should be 11", efce.getBoxGroup().getBoxes().get(1).getIp(),
				equalTo("192.168.2.11"));
		assertThat("first box should be 12", efce.getBoxGroup().getBoxes().get(2).getIp(),
				equalTo("192.168.2.14"));

		String yml = appObjectMappers.getYmlObjectMapper().writeValueAsString(efce);
		Path testFolder = sfolder.getTestPath();
		Files.write(testFolder.resolve("envforcodeexec.yaml"), yml.getBytes());

		String xml = appObjectMappers.getXmlObjectMapper().writeValueAsString(efce);
		Files.write(testFolder.resolve("envforcodeexec.xml"), xml.getBytes());

		String json = appObjectMappers.getObjectMapper().writeValueAsString(efce);
		Files.write(testFolder.resolve("envforcodeexec.json"), json.getBytes());

	}

	private void writeEnvFixturesFromFolder(Person root, BoxGroup onebg) throws IOException {
		Stream<Path> folders = Files.list(softwarePath);

		folders.filter(Files::isDirectory).map(SoftwareFolder::new).filter(SoftwareFolder::isValid).forEach(sfolder -> {
			try {
				Software sf = ymlObjectMapper.readValue(sfolder.readDescriptionyml(), Software.class);
				sf.setName(sfolder.getName());
				sf.setOstype(sfolder.getOstype());
				sf.setSversion(sfolder.getSversion());
				sf.setConfigContent(sfolder.getConfigContent(sf.getConfigContent()));
				ConfigContent cconfig = new ConfigContent(sf.getConfigContent());
				cconfig.getConverted(appObjectMappers);

			} catch (Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		});
	}

	private void testBoxGroup() throws IOException {
		Resource resource = context.getResource("classpath:fixtures/domain/boxgroup.yaml");
		BoxGroup bg = ymlObjectMapper.readValue(resource.getInputStream(), BoxGroup.class);
		Person root = getFirstPerson();

		Resource ccResource = context.getResource("classpath:fixtures/domain/" + bg.getConfigContent());
		if (ccResource != null) {
			bg.setConfigContent(
					CharStreams.toString(new InputStreamReader(ccResource.getInputStream(), Charsets.UTF_8)));
		}

		BoxGroup bgInDb = boxGroupRepository.findByName(bg.getName());

		if (bgInDb != null) {
			bgInDb.getBoxes().forEach(b -> {
				b.getBoxGroups().remove(bgInDb);
				boxRepository.save(b);
			});
			boxGroupRepository.delete(bgInDb);
		}

		bg.setCreator(root);

		bg = boxGroupRepository.save(bg);

		final Set<BoxGroup> bgs = Sets.newHashSet(bg);

		Set<Box> boxes = bg.getBoxes().stream().map(box -> {
			Box boxInDb = boxRepository.findByIp(box.getIp());
			if (boxInDb != null) {
				boxRepository.delete(boxInDb);
			}
			box.setCreator(root);
			box.setBoxGroups(bgs);
			return boxRepository.save(box);
		}).collect(Collectors.toSet());

		bg.setBoxes(boxes);
		bgs.add(boxGroupRepository.save(bg));

		BoxGroup onebg = bgs.iterator().next();

	}

	@Test
	public void verify() throws IOException {
		testBoxGroup();
		if (!Files.exists(softwarePath)) {
			return;
		}
		try (Stream<Path> folders = Files.list(softwarePath)) {
			folders.filter(Files::isDirectory).map(SoftwareFolder::new).filter(SoftwareFolder::isValid)
					.forEach(sfolder -> {
						try {
							Software sf = ymlObjectMapper.readValue(sfolder.readDescriptionyml(), Software.class);
							sf.setName(sfolder.getName());
							sf.setOstype(sfolder.getOstype());
							sf.setSversion(sfolder.getSversion());
							sf.setConfigContent(sfolder.getConfigContent(sf.getConfigContent()));
							ConfigContent cconfig = new ConfigContent(sf.getConfigContent());
							cconfig.getConverted(appObjectMappers);

							EnvForCodeExec efce = null;
							// do convert.
							if (!(Strings.isNullOrEmpty(cconfig.getFrom()) || Strings.isNullOrEmpty(cconfig.getTo()))) {
								if ("XML".equals(cconfig.getTo())) {
									String xml = com.google.common.io.Files
											.asCharSource(sfolder.getTestPath().resolve(tbasename + "xml").toFile(),
													Charsets.UTF_8)
											.read();
									efce = appObjectMappers.getXmlObjectMapper().readValue(xml, EnvForCodeExec.class);
									assertTrue("should be xml content.",
											efce.getSoftware().getConfigContent().startsWith("<LinkedHashMap>"));
								}
							}

						} catch (Exception e) {
							e.printStackTrace();
						}
					});
		}
	}
}
