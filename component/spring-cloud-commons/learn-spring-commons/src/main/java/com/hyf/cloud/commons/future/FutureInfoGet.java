package com.hyf.cloud.commons.future;

/**
 * @author baB_hyf
 * @date 2021/05/16
 */
public class FutureInfoGet {

	public static void main(String[] args) {
		Class<?> futureInfoClass = String.class;

		String className = futureInfoClass.getName();
		String simpleName = futureInfoClass.getSimpleName();
		String typeName = futureInfoClass.getTypeName();
		String canonicalName = futureInfoClass.getCanonicalName();

		System.out.println("className: " + className);
		System.out.println("simpleName: " + simpleName);
		System.out.println("typeName: " + typeName);
		System.out.println("canonicalName: " + canonicalName);

		System.out.println("-------------------------------------");

		Package pkg = futureInfoClass.getPackage();
		String pkgName = pkg.getName();
		boolean sealed = pkg.isSealed();

		String implementationTitle = pkg.getImplementationTitle();
		String implementationVersion = pkg.getImplementationVersion();
		String implementationVendor = pkg.getImplementationVendor();

		String specificationTitle = pkg.getSpecificationTitle();
		String specificationVersion = pkg.getSpecificationVersion();
		String specificationVendor = pkg.getSpecificationVendor();

		System.out.println("packageName: " + pkgName);
		System.out.println("sealed: " + sealed);
		System.out.println("implementationTitle: " + implementationTitle);
		System.out.println("implementationVersion: " + implementationVersion);
		System.out.println("implementationVendor: " + implementationVendor);
		System.out.println("specificationTitle: " + specificationTitle);
		System.out.println("specificationVersion: " + specificationVersion);
		System.out.println("specificationVendor: " + specificationVendor);

	}
}
