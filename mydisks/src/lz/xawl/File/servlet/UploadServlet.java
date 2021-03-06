package lz.xawl.File.servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lz.xawl.Catalog.domain.Catalog;
import lz.xawl.Catalog.service.CatalogService;
import lz.xawl.File.service.FileService;
import lz.xawl.Util.Tool;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

public class UploadServlet extends HttpServlet {

	private FileService fileservice = new FileService();
	private CatalogService catalogservice = new CatalogService();

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		// 如果要实现不同用户不同文件夹，这里需要拿到user用户
		PrintWriter out = resp.getWriter();

		req.setCharacterEncoding("utf-8");
		resp.setContentType("text/html;charset=utf-8");
		// 插件工厂
		FileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload(factory);
		upload.setSizeMax(1024 * 1024 * 500);// 50M
		upload.setHeaderEncoding("utf-8");
		myProgressListener getBarListener = new myProgressListener(req);
		upload.setProgressListener(getBarListener);
		List<FileItem> fileList = null;

		try {
			fileList = upload.parseRequest(req);
		} catch (Exception e) {
			out.write("<script>alert('大小超出限制');</script>");
			return;
		}
		// 获取图片
		String hash = null;
		FileItem file = fileList.get(0);
		String cid = fileList.get(1).getString("utf-8");
		try {
			hash = Tool.getMD5Checksum(file.getInputStream());
			// 得到了hash 应该去数据库查询一下 如果已经存在就不上传了

			lz.xawl.File.domain.File f = fileservice.findByHash(hash);

			if (f != null) {
				// 说明已经存在改文件了直接写入数据库中
				System.out.println("文件名:" + f.getfName());
				lz.xawl.File.domain.File file2 = new lz.xawl.File.domain.File();
				file2.setfPath(f.getfPath());
				file2.setfSize(f.getfSize());
				file2.setfType(f.getfType());
				file2.setfName(f.getfName());
				file2.setfHash("");
				file2.setfDiskName(f.getfDiskName());
				String fid = Tool.randomId(); // 文件id
				file2.setfId(fid);
				file2.setfDowncount("0");
				file2.setfDesc("暂留");
				SimpleDateFormat simp1 = new SimpleDateFormat("yyyy-MM-dd");
				String date = simp1.format(new Date()).toString();
				file2.setfUploadtime(date);
				file2.setIsShare("0");
				Catalog c1 = new Catalog();
				c1.setcId(cid);
				file2.setCatalog(c1);
				fileservice.upLoadFile(file2);
				String cf = catalogservice.cidTocf(cid);
				if (cf == null || cf.isEmpty() || "".equals(cf)) {
					// 说明文件夹下暂时没有文件
					String sCf = Tool.randomId();// 生成cf值
					catalogservice.intoCf(cid, sCf);
					// 开始写入fid值
					fileservice.writecf(fid, sCf);
				} else {
					fileservice.writecf(fid, cf);
				}
				out.write("<script>alert('上传完成');window.location.href='/mydisks/CatalogServlet?method=myCatalog&cid="
						+ cid + "'</script>");
				return;
			}

		} catch (Exception e1) {
			e1.printStackTrace();
		}

		String filename = file.getName();
		String fileQian = null; // 文件名，不包含后缀
		String fileExt = null; // 后缀文件类型
		// 截图名字名 部分浏览器给的是绝对路径
		int indes = filename.lastIndexOf("\\");
		if (indes != -1) {
			filename = filename.substring(indes + 1);
		}

		int indes2 = filename.lastIndexOf(".");
		if (indes2 != -1) {
			fileQian = filename.substring(0, indes2);

			fileExt = filename.substring(indes2 + 1);

		}

		// 开始上传文件 文件名重名问题需要解决 并且按用户分文件夹！
		String savePath = this.getServletContext().getRealPath("/upload");
		// 看是否存在 不存在就要创建
		File path = new File(savePath);
		if (!path.exists()) {
			path.mkdirs();
		}
		String filename2 = fileQian
				+ " "
				+ new SimpleDateFormat("hh-mm-ss").format(new Date())
						.toString() + "." + fileExt;

		File imgfile = new File(savePath, filename2); // 绝对路径的文件

		try {
			file.write(imgfile);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// 上传完成 开始写数据库保存
		lz.xawl.File.domain.File myfile = new lz.xawl.File.domain.File();
		myfile.setfPath("/upload/" + filename2);
		myfile.setfSize(file.getSize());
		myfile.setfType(fileExt);
		myfile.setfName(filename);
		myfile.setfHash(hash);
		myfile.setfDiskName(filename2);
		String fid = Tool.randomId(); // 文件id
		myfile.setfId(fid);
		myfile.setfDowncount("0");
		myfile.setfDesc("暂留");
		SimpleDateFormat simp = new SimpleDateFormat("yyyy-MM-dd");
		String date = simp.format(new Date()).toString();
		myfile.setfUploadtime(date);
		myfile.setIsShare("0");
		Catalog c = new Catalog();
		c.setcId(cid);
		myfile.setCatalog(c);
		fileservice.upLoadFile(myfile);// 文件表写好了还需要写2个表
		// 先判断对应的文件夹是否有文件cf 值是否为空
		String cf = catalogservice.cidTocf(cid);
		if (cf == null || cf.isEmpty() || "".equals(cf)) {
			// 说明文件夹下暂时没有文件
			String sCf = Tool.randomId();// 生成cf值
			catalogservice.intoCf(cid, sCf);
			// 开始写入fid值
			fileservice.writecf(fid, sCf);
		} else {
			fileservice.writecf(fid, cf);
		}
		// 数据表写完成了，
		// PrintWriter out=resp.getWriter();

		out.write("<script>alert('upload ok!');window.location.href='/mydisks/CatalogServlet?method=myCatalog&cid="
				+ cid + "'</script>");
	}

}
