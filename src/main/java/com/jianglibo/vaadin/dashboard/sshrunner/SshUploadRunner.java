package com.jianglibo.vaadin.dashboard.sshrunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.jianglibo.vaadin.dashboard.config.ApplicationConfig;
import com.jianglibo.vaadin.dashboard.domain.BoxHistory;
import com.jianglibo.vaadin.dashboard.ssh.JschSession;
import com.jianglibo.vaadin.dashboard.taskrunner.OneThreadTaskDesc;
import com.jianglibo.vaadin.dashboard.util.ThrowableUtil;
import com.jianglibo.vaadin.dashboard.vo.FileToUploadVo;

/**
 * ApplicationConfig has a configurable remoteFolder property. Files upload to
 * that folder.
 * 
 * @author jianglibo@gmail.com
 *
 */
@Component()
public class SshUploadRunner implements BaseRunner {

	@Autowired
	private ApplicationConfig applicationConfig;

	@Override
	public void run(JschSession jsession,  OneThreadTaskDesc taskDesc) {

		// confirm all files exists in local.
		List<String> noneExistsFiles = Lists.newArrayList();
		
		Set<FileToUploadVo> ftuvos = taskDesc.getSoftware().getFileToUploadVos();
		
		ftuvos.forEach(fvo -> {
			Path sourceFile = applicationConfig.getLocalFolderPath().resolve(fvo.getRelative()); 
			if (!Files.exists(sourceFile)) {
				noneExistsFiles.add(sourceFile.toAbsolutePath().toString());
			}
		});
		
		BoxHistory bh = taskDesc.getBoxHistory();
		if (noneExistsFiles.size() > 0) {
			String log = noneExistsFiles.stream().reduce("", (result, l) -> result + l);
			bh.appendLogAndSetFailure(log);
		} else {
			try {
				for(FileToUploadVo fvo : taskDesc.getSoftware().getFileToUploadVos()) {
					ChannelSftp sftp = null;
					try {
						sftp = jsession.getSftpCh();
						putOneFile(sftp, fvo);
					} catch (Exception e) {
						bh.appendLogAndSetFailure(ThrowableUtil.printToString(e));
						bh.setSuccess(false);
					} finally {
						if (sftp != null) {
							sftp.disconnect();
						}
					}
				}
			} catch (Exception e) {
				bh.appendLogAndSetFailure(ThrowableUtil.printToString(e));
			}
		}
	}

	private void putOneFile(ChannelSftp sftp, FileToUploadVo fvo) throws JSchException, SftpException {
		String fileToUpload = applicationConfig.getLocalFolderPath().resolve(fvo.getRelative()).toAbsolutePath().toString().replace("\\\\", "/");
		String targetFile = applicationConfig.getRemoteFolder() + fvo.getRelative().replaceAll("\\\\", "/");
		
		sftp.connect();
		int idx = targetFile.lastIndexOf('/');
		String targetFolder = targetFile.substring(0, idx);
		
		try {
			sftp.mkdir(targetFolder);
		} catch (Exception e) {
			// will throw exception if exists.
			e.printStackTrace();
		}

		sftp.put(fileToUpload, targetFile, ChannelSftp.OVERWRITE);
	}
}
