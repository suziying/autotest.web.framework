/**
 * http://surenpi.com
 */
package org.suren.autotest.web.framework.code;

/**
 * 生成器
 * @author suren
 * @date 2016年12月3日 下午8:41:01
 */
public interface Generator
{
	/**
	 * 根据源文件生成Java代码
	 * @param srcCoding 源文件路径
	 * @param outputDir 输出的目录
	 * @return
	 */
	void generate(String srcCoding, String outputDir);
}
